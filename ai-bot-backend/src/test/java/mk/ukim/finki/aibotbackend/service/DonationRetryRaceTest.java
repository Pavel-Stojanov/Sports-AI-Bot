package mk.ukim.finki.aibotbackend.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void lockedCandidateMustObserveCommittedRetryDeadline() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Long> batchIds = transaction.execute(status -> {
            ExtractionSession session = sessions.save(new ExtractionSession(
                SocialNetwork.SPORTS_PORTAL_GOL, "retry race"));
            return java.util.stream.IntStream.range(0, 2).mapToObj(index -> {
                DonationBatch batch = batches.save(new DonationBatch(DonationStatus.SUBMITTED));
                ExtractedPost post = new ExtractedPost(session, "retry-race-" + index, "gol.mk",
                    "Вардар победи со 3:1 во првенството.", "https://www.gol.mk/fudbal/race-" + index, null, 0.95);
                post.setDonationBatch(batch);
                posts.save(post);
                return batch.getId();
            }).toList();
        });
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
}
