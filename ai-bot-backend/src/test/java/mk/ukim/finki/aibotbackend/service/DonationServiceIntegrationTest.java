package mk.ukim.finki.aibotbackend.service;

import jakarta.transaction.Transactional;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationItemResult;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationResponse;
import mk.ukim.finki.aibotbackend.integration.vezilka.VezilkaClient;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.repository.DonationBatchRepository;
import mk.ukim.finki.aibotbackend.repository.ExtractedPostRepository;
import mk.ukim.finki.aibotbackend.repository.ExtractionSessionRepository;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration-test the donation workflow (createBatch -> approve -> submit)
 * with the full Spring context and a mocked VezilkaClient.
 */
@SpringBootTest
@Testcontainers
@Transactional
public class DonationServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("aibot_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "integration-test-secret-0123456789abcdef0123456789abcdef");
    }

    @MockitoBean
    private VezilkaClient vezilkaClient;

    @Autowired
    private DonationService donationService;

    @Autowired
    private ExtractionSessionRepository extractionSessionRepository;

    @Autowired
    private ExtractedPostRepository extractedPostRepository;

    @Autowired
    private DonationBatchRepository donationBatchRepository;

    private ExtractedPost post;

    @BeforeEach
    void setUp() {
        ExtractionSession session = extractionSessionRepository.save(
            new ExtractionSession(SocialNetwork.SPORTS_PORTAL_GOL, "test session"));
        post = extractedPostRepository.save(new ExtractedPost(
            session, "vardar-pobedi", "gol.mk",
            "Вардар победи со 3:1 во првенството.",
            "https://www.gol.mk/fudbal/vardar-pobedi", null, 0.95));
    }

    private static DonationResponse acceptedResponse(String id) {
        return new DonationResponse(
            List.of(new DonationItemResult(id, "accepted", "text", false, 0, null)),
            1, 1, 0, 0);
    }

    @Test
    void testDonationWorkflow() {
        when(vezilkaClient.submitTextDonations(any()))
            .thenReturn(acceptedResponse("VEZ-123"));

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        assertThat(batch.getStatus()).isEqualTo(DonationStatus.DRAFT);
        assertThat(batch.getPosts()).hasSize(1);

        assertThat(donationService.approve(batch.getId()).getStatus())
            .isEqualTo(DonationStatus.APPROVED);

        // Vezilka decides synchronously, so submit() already knows the verdict.
        DonationBatch submitted = donationService.submit(batch.getId());
        assertThat(submitted.getStatus()).isEqualTo(DonationStatus.ACCEPTED);
        assertThat(submitted.getVezilkaReference()).isEqualTo("VEZ-123");
        assertThat(submitted.getSubmittedAt()).isNotNull();
        assertThat(extractedPostRepository.findById(post.getId()).orElseThrow())
            .satisfies(donated -> {
                assertThat(donated.getVezilkaId()).isEqualTo("VEZ-123");
                assertThat(donated.getRejectionReason()).isNull();
            });
    }

    @Test
    void testRejectedPostLeavesBatchRejectedWithReason() {
        when(vezilkaClient.submitTextDonations(any())).thenReturn(new DonationResponse(
            List.of(new DonationItemResult("VEZ-456", "rejected", "text", false, 0, "not_macedonian")),
            1, 0, 1, 0));

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());

        DonationBatch submitted = donationService.submit(batch.getId());
        assertThat(submitted.getStatus()).isEqualTo(DonationStatus.REJECTED);
        assertThat(submitted.getVezilkaReference()).isNull();
        assertThat(extractedPostRepository.findById(post.getId()).orElseThrow().getRejectionReason())
            .isEqualTo("not_macedonian");
    }

    @Test
    void testDedupedPostCountsAsAccepted() {
        when(vezilkaClient.submitTextDonations(any())).thenReturn(new DonationResponse(
            List.of(new DonationItemResult("VEZ-789", "rejected", "text", true, 0, null)),
            1, 0, 0, 1));

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());

        assertThat(donationService.submit(batch.getId()).getStatus())
            .isEqualTo(DonationStatus.ACCEPTED);
    }

    @Test
    void testFailedSubmissionLeavesBatchApproved() {
        when(vezilkaClient.submitTextDonations(any()))
            .thenThrow(new VezilkaIntegrationException("Vezilka rate limit exceeded"));

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());

        // Nothing reached the corpus, so the batch stays submittable.
        assertThatThrownBy(() -> donationService.submit(batch.getId()))
            .isInstanceOf(VezilkaIntegrationException.class);
    }

    @Test
    void testRefreshSettlesSubmittedBatch() {
        when(vezilkaClient.submitTextDonations(any())).thenReturn(acceptedResponse("VEZ-321"));
        when(vezilkaClient.checkStatus("VEZ-321")).thenReturn(DonationStatus.ACCEPTED);

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());
        donationService.submit(batch.getId());
        // A batch cut short by the rate limit is left SUBMITTED with its
        // verdicts; reproduce that state and let the scheduler settle it.
        batch.setStatus(DonationStatus.SUBMITTED);
        donationBatchRepository.save(batch);

        donationService.refreshSubmittedStatuses();

        assertThat(donationService.findById(batch.getId()).orElseThrow().getStatus())
            .isEqualTo(DonationStatus.ACCEPTED);
    }

    @Test
    void testCreateBatchWithUnknownPostThrows() {
        assertThatThrownBy(() -> donationService.createBatch(List.of(999_999L)))
            .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void testApproveFromWrongStateThrows() {
        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());

        assertThatThrownBy(() -> donationService.approve(batch.getId()))
            .isInstanceOf(InvalidDonationStateException.class);
    }
}
