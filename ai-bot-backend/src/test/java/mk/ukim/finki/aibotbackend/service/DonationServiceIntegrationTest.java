package mk.ukim.finki.aibotbackend.service;

import jakarta.transaction.Transactional;
import mk.ukim.finki.aibotbackend.integration.vezilka.DonationReceipt;
import mk.ukim.finki.aibotbackend.integration.vezilka.VezilkaClient;
import mk.ukim.finki.aibotbackend.model.domain.DonationBatch;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.exception.InvalidDonationStateException;
import mk.ukim.finki.aibotbackend.model.exception.PostNotFoundException;
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

    @Test
    void testDonationWorkflow() {
        when(vezilkaClient.submitTextDonation(any()))
            .thenReturn(new DonationReceipt("VEZ-123", "received"));

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        assertThat(batch.getStatus()).isEqualTo(DonationStatus.DRAFT);
        assertThat(batch.getPosts()).hasSize(1);

        assertThat(donationService.approve(batch.getId()).getStatus())
            .isEqualTo(DonationStatus.APPROVED);

        DonationBatch submitted = donationService.submit(batch.getId());
        assertThat(submitted.getStatus()).isEqualTo(DonationStatus.SUBMITTED);
        assertThat(submitted.getVezilkaReference()).isEqualTo("VEZ-123");
        assertThat(submitted.getSubmittedAt()).isNotNull();
    }

    @Test
    void testRefreshSubmittedStatuses() {
        when(vezilkaClient.submitTextDonation(any()))
            .thenReturn(new DonationReceipt("VEZ-456", "received"));
        when(vezilkaClient.checkStatus("VEZ-456")).thenReturn(DonationStatus.ACCEPTED);

        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());
        donationService.submit(batch.getId());

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
        when(vezilkaClient.submitTextDonation(any()))
            .thenReturn(new DonationReceipt("VEZ-789", "received"));
        DonationBatch batch = donationService.createBatch(List.of(post.getId()));
        donationService.approve(batch.getId());

        assertThatThrownBy(() -> donationService.approve(batch.getId()))
            .isInstanceOf(InvalidDonationStateException.class);
    }
}
