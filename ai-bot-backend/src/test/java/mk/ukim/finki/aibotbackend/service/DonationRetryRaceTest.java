package mk.ukim.finki.aibotbackend.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import mk.ukim.finki.aibotbackend.integration.vezilka.BatchVezilkaClient;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationItemResult;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationResponse;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.repository.DonationBatchRepository;
import mk.ukim.finki.aibotbackend.repository.ExtractedPostRepository;
import mk.ukim.finki.aibotbackend.repository.ExtractionSessionRepository;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class DonationRetryRaceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DonationBatchRepository batches;
    @Autowired ExtractionSessionRepository sessions;
    @Autowired ExtractedPostRepository posts;
    @Autowired DonationService donations;
    @MockitoBean BatchVezilkaClient client;

    @AfterEach
    void cleanUp() {
        // Both tests share the container; a batch left SUBMITTED would be retried by the next test.
        posts.deleteAll();
        batches.deleteAll();
        sessions.deleteAll();
    }

    private List<Long> twoSubmittedBatches(TransactionTemplate transaction, String slug) {
        return transaction.execute(status -> {
            ExtractionSession session = sessions.save(new ExtractionSession(
                SocialNetwork.SPORTS_PORTAL_GOL, slug));
            return IntStream.range(0, 2).mapToObj(index -> {
                DonationBatch batch = batches.save(new DonationBatch(DonationStatus.SUBMITTED));
                ExtractedPost post = new ExtractedPost(session, slug + "-" + index, "gol.mk",
                    "Вардар победи со 3:1 во првенството.", "https://www.gol.mk/fudbal/" + slug + "-" + index, null, 0.95);
                post.setDonationBatch(batch);
                posts.save(post);
                return batch.getId();
            }).toList();
        });
    }

    @Test
    void lockedCandidateMustObserveCommittedRetryDeadline() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Long> batchIds = twoSubmittedBatches(transaction, "retry-race");
        Long laterBatchId = batchIds.get(1);
        Instant retryAt = Instant.now().plusSeconds(3600);
        AtomicBoolean firstSubmission = new AtomicBoolean(true);
        when(client.submitTextDonations(any())).thenAnswer(invocation -> {
            // While the scheduler submits the first batch, another request delays the second.
            if (firstSubmission.getAndSet(false)) {
                concurrent.executeWithoutResult(other -> {
                    DonationBatch changed = batches.findForUpdate(laterBatchId).orElseThrow();
                    changed.setNextRetryAt(retryAt);
                    batches.save(changed);
                });
            }
            return new DonationResponse(
                List.of(new DonationItemResult("accepted", "accepted", "text", false, 0, null)), 1, 1, 0, 0);
        });

        donations.refreshSubmittedStatuses();

        verify(client, times(1)).submitTextDonations(any());
        transaction.executeWithoutResult(status -> {
            DonationBatch delayed = batches.findById(laterBatchId).orElseThrow();
            assertThat(delayed.getNextRetryAt()).isAfter(Instant.now());
            assertThat(delayed.getStatus()).isEqualTo(DonationStatus.SUBMITTED);
            assertThat(delayed.getPosts()).allSatisfy(post -> assertThat(post.getDonationStatus()).isNull());
        });
    }

    @Test
    void laterBatchPersistenceFailureKeepsEarlierVerdicts() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> batchIds = twoSubmittedBatches(transaction, "retry-isolation");
        AtomicInteger submissions = new AtomicInteger();
        when(client.submitTextDonations(any())).thenAnswer(invocation -> submissions.getAndIncrement() == 0
            ? new DonationResponse(
                List.of(new DonationItemResult("first-id", "accepted", "text", false, 0, null)), 1, 1, 0, 0)
            // rejection_reason is varchar(255); storing this verdict fails at commit.
            : new DonationResponse(
                List.of(new DonationItemResult(null, "rejected", "text", false, 0, "x".repeat(300))), 1, 0, 1, 0));

        donations.refreshSubmittedStatuses();

        verify(client, times(2)).submitTextDonations(any());
        transaction.executeWithoutResult(status -> {
            DonationBatch first = batches.findById(batchIds.get(0)).orElseThrow();
            assertThat(first.getStatus()).isEqualTo(DonationStatus.ACCEPTED);
            assertThat(first.getPosts()).allSatisfy(post -> assertThat(post.getVezilkaId()).isEqualTo("first-id"));
            DonationBatch second = batches.findById(batchIds.get(1)).orElseThrow();
            assertThat(second.getStatus()).isEqualTo(DonationStatus.SUBMITTED);
            assertThat(second.getPosts()).allSatisfy(post -> assertThat(post.getDonationStatus()).isNull());
        });
    }
}
