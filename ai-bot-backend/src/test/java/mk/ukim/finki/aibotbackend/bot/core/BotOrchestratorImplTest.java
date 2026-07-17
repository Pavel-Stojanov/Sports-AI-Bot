package mk.ukim.finki.aibotbackend.bot.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import mk.ukim.finki.aibotbackend.model.enums.SessionStatus;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.enums.TargetType;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration-tests {@link BotOrchestratorImpl#runSession} against the full
 * Spring context, driving it on the test thread the way the async listener
 * does — with a detached session whose targets are LAZY. Deliberately NOT
 * {@code @Transactional}: a test-managed persistence context would keep the
 * session attached and mask the LazyInitializationException this test guards.
 */
@SpringBootTest
@Testcontainers
public class BotOrchestratorImplTest {
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
    private SocialNetworkBot socialNetworkBot;

    @Autowired
    private BotOrchestrator botOrchestrator;

    @Autowired
    private ExtractionSessionService extractionSessionService;

    @Autowired
    private ExtractedPostService extractedPostService;

    private Long createRunningSessionWithTarget() {
        ExtractionSession session =
            new ExtractionSession(SocialNetwork.SPORTS_PORTAL_GOL, "orchestrator test");
        session.getTargets().add(
            new ExtractionTarget(TargetType.FEED_URL, "https://www.gol.mk/", session));
        Long id = extractionSessionService.create(session).getId();
        extractionSessionService.start(id);
        return id;
    }

    @Test
    void runSessionCompletesAndPersistsExtractedPost() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of(
            new CreateExtractedPostDto(
                "gol-1", "gol.mk", "Вардар победи со 3:1.", "Вардар победи.",
                "https://www.gol.mk/fudbal/vardar", null, 0.95, List.of())));

        botOrchestrator.runSession(sessionId);

        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.COMPLETED);
        List<ExtractedPost> persisted = extractedPostService
            .findAll(new PostFilterDto(sessionId, null, null, null, null), 0, 10)
            .getContent();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).getExternalId()).isEqualTo("gol-1");
        verify(socialNetworkBot).shutdown();
    }

    @Test
    void runSessionFailsButStillShutsDownWhenBotThrows() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any()))
            .thenThrow(new RuntimeException("boom"));

        botOrchestrator.runSession(sessionId);

        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.FAILED);
        verify(socialNetworkBot).shutdown();
    }
}
