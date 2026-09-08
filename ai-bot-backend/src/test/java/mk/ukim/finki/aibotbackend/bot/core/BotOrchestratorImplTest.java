package mk.ukim.finki.aibotbackend.bot.core;

import mk.ukim.finki.aibotbackend.bot.llm.BotAction;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;

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

    @Autowired
    private mk.ukim.finki.aibotbackend.service.application.ExtractionSessionApplicationService applicationService;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("botExecutor")
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor botExecutor;

    @Test
    void concurrentApiStartsQueueBehindTheActiveBrowserRun() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(2);
        when(socialNetworkBot.execute(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Test release timed out");
            return List.of(new CreateExtractedPostDto("queued", "gol.mk", "Вардар победи во натпреварот.",
                null, "https://www.gol.mk/fudbal/queued", null, 0.9, List.of()));
        });
        org.mockito.Mockito.doAnswer(invocation -> { finished.countDown(); return null; })
            .when(socialNetworkBot).shutdown();
        ExtractionSession first = new ExtractionSession(SocialNetwork.SPORTS_PORTAL_GOL, "first queued run");
        first.getTargets().add(new ExtractionTarget(TargetType.FEED_URL, "https://www.gol.mk/", first));
        ExtractionSession second = new ExtractionSession(SocialNetwork.SPORTS_PORTAL_GOL, "second queued run");
        second.getTargets().add(new ExtractionTarget(TargetType.FEED_URL, "https://www.gol.mk/", second));
        Long firstId = extractionSessionService.create(first).getId();
        Long secondId = extractionSessionService.create(second).getId();
        applicationService.start(firstId);
        try {
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            applicationService.start(secondId);
            assertThat(botExecutor.getThreadPoolExecutor().getQueue()).hasSize(1);
            verify(socialNetworkBot, org.mockito.Mockito.times(1)).login();
        } finally {
            release.countDown();
        }
        assertThat(finished.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(extractionSessionService.findById(firstId).orElseThrow().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(extractionSessionService.findById(secondId).orElseThrow().getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

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
    void staleQueuedExecutionDoesNotOpenBrowser() {
        Long sessionId = createRunningSessionWithTarget();
        long oldExecution = extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber();
        extractionSessionService.stop(sessionId);
        extractionSessionService.start(sessionId);
        botOrchestrator.runSession(sessionId, oldExecution);
        org.mockito.Mockito.verifyNoInteractions(socialNetworkBot);
        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.RUNNING);
    }

    @Test
    void oldFailureCannotOverwriteResumedSession() {
        Long sessionId = createRunningSessionWithTarget();
        long oldExecution = extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber();
        when(socialNetworkBot.execute(any(), any())).thenAnswer(invocation -> {
            extractionSessionService.stop(sessionId);
            extractionSessionService.start(sessionId);
            throw new RuntimeException("Old request failed after resume");
        });
        botOrchestrator.runSession(sessionId, oldExecution);
        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.RUNNING);
    }

    @Test
    void resumedExecutionDoesNotDuplicateSavedPosts() {
        Long sessionId = createRunningSessionWithTarget();
        var session = extractionSessionService.findById(sessionId).orElseThrow();
        extractedPostService.saveAll(List.of(new ExtractedPost(session, "already-saved", "gol.mk",
            "Вардар победи во натпреварот.", "https://www.gol.mk/fudbal/saved", null, 0.9)));
        extractionSessionService.stop(sessionId);
        extractionSessionService.start(sessionId);
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of(new CreateExtractedPostDto(
            "already-saved", "gol.mk", "Вардар победи во натпреварот.", null,
            "https://www.gol.mk/fudbal/saved", null, 0.9, List.of())));
        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());
        assertThat(extractedPostService.findAll(new PostFilterDto(sessionId, null, null, null, null), 0, 10)
            .getTotalElements()).isEqualTo(1);
    }

    @Test
    void emptyExtractionIsNotReportedAsCompleted() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of());
        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());
        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void stopDuringExecutionLeavesSessionPaused() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any())).thenAnswer(invocation -> {
            extractionSessionService.stop(sessionId);
            BotStepListener listener = invocation.getArgument(1);
            listener.onStep(new BotAction(
                BotActionType.WAIT, null, null, "pause test"), true);
            return List.of();
        });
        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());
        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.PAUSED);
        verify(socialNetworkBot).shutdown();
    }

    @Test
    void failedExtractionKeepsEarlierArticlesOfTheTarget() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any())).thenAnswer(invocation -> {
            BotStepListener listener = invocation.getArgument(1);
            listener.onStep(new BotAction(BotActionType.EXTRACT, null, null, "first article"), true);
            listener.onStep(new BotAction(BotActionType.EXTRACT, null, null, "unparseable page"), false);
            return List.of(new CreateExtractedPostDto(
                "gol-kept", "gol.mk", "Вардар победи со 3:1.", null,
                "https://www.gol.mk/fudbal/kept", null, 0.95, List.of()));
        });

        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());

        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.COMPLETED);
        assertThat(extractedPostService.findAll(new PostFilterDto(sessionId, null, null, null, null), 0, 10)
            .getTotalElements()).isEqualTo(1);
    }

    @Test
    void runSessionCompletesAndPersistsExtractedPost() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of(
            new CreateExtractedPostDto(
                "gol-1", "gol.mk", "Вардар победи со 3:1.", "Вардар победи.",
                "https://www.gol.mk/fudbal/vardar", null, 0.95, List.of())));

        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());

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
    void runSessionDeduplicatesRepeatedExtractsByExternalId() {
        Long sessionId = createRunningSessionWithTarget();
        CreateExtractedPostDto post = new CreateExtractedPostDto(
            "gol-dup", "gol.mk", "Пелистер победи со 2:0.", "Пелистер победи.",
            "https://www.gol.mk/fudbal/pelister", null, 0.9, List.of());
        when(socialNetworkBot.execute(any(), any())).thenReturn(List.of(post, post, post));

        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());

        List<ExtractedPost> persisted = extractedPostService
            .findAll(new PostFilterDto(sessionId, null, null, null, null), 0, 10)
            .getContent();
        assertThat(persisted).hasSize(1);
    }

    @Test
    void runSessionFailsButStillShutsDownWhenBotThrows() {
        Long sessionId = createRunningSessionWithTarget();
        when(socialNetworkBot.execute(any(), any()))
            .thenThrow(new RuntimeException("boom"));

        botOrchestrator.runSession(sessionId, extractionSessionService.findById(sessionId).orElseThrow().getExecutionNumber());

        assertThat(extractionSessionService.findById(sessionId).orElseThrow().getStatus())
            .isEqualTo(SessionStatus.FAILED);
        verify(socialNetworkBot).shutdown();
    }
}
