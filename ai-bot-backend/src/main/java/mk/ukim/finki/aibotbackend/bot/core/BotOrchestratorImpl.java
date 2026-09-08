package mk.ukim.finki.aibotbackend.bot.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.llm.BotAction;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.service.domain.BotActionLogService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class BotOrchestratorImpl implements BotOrchestrator {
    private final SocialNetworkBot socialNetworkBot;
    private final ExtractionSessionService extractionSessionService;
    private final ExtractedPostService extractedPostService;
    private final BotActionLogService botActionLogService;
    private final TransactionTemplate transactionTemplate;

    public BotOrchestratorImpl(
        SocialNetworkBot socialNetworkBot,
        ExtractionSessionService extractionSessionService,
        ExtractedPostService extractedPostService,
        BotActionLogService botActionLogService,
        TransactionTemplate transactionTemplate
    ) {
        this.socialNetworkBot = socialNetworkBot;
        this.extractionSessionService = extractionSessionService;
        this.extractedPostService = extractedPostService;
        this.botActionLogService = botActionLogService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public synchronized void runSession(Long sessionId, long executionNumber) {
        // Runs on the async bot thread: load the session and materialize its
        // LAZY targets inside a short transaction so the rest of the (long)
        // run can work on a detached entity without a LazyInitializationException
        // and without holding a DB connection for the whole run.
        ExtractionSession session = transactionTemplate.execute(status -> {
            ExtractionSession loaded = extractionSessionService
                .findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
            loaded.getTargets().size();
            return loaded;
        });

        // A queued run whose session was stopped or restarted meanwhile must not open a browser.
        if (!extractionSessionService.isRunning(sessionId, executionNumber)) {
            return;
        }
        try {
            socialNetworkBot.login();
            // The LLM sometimes EXTRACTs the same page more than once despite the
            // prompt rules, so posts are deduplicated by externalId across the run.
            Set<String> seenExternalIds = new HashSet<>(
                extractedPostService.findExternalIdsBySessionId(sessionId));
            // Counts what this execution extracted. Posts saved by an earlier
            // execution must not make an empty resumed run look successful.
            int extractedCount = 0;
            for (ExtractionTarget target : session.getTargets()) {
                requireRunning(sessionId, executionNumber);
                List<CreateExtractedPostDto> extracted = socialNetworkBot.execute(
                    target,
                    (action, successful) -> {
                        // A failed step stays in the trace and the loop goes on. Leaving the
                        // loop here would drop the articles this target has already collected.
                        botActionLogService.log(session, action, successful);
                        requireRunning(sessionId, executionNumber);
                    });
                requireRunning(sessionId, executionNumber);
                extractedCount += extracted.size();
                extractedPostService.saveAll(
                    extracted.stream()
                        .filter(dto -> dto.externalId() == null || seenExternalIds.add(dto.externalId()))
                        .map(dto -> dto.toExtractedPost(session))
                        .toList());
            }
            if (extractedCount == 0) {
                throw new BotExecutionException("No articles were extracted. Check the target and bot trace.");
            }
            extractionSessionService.finishExecution(sessionId, executionNumber, true);
        } catch (SessionPausedException exception) {
            log.info("Extraction session {} execution {} stopped.", sessionId, executionNumber);
        } catch (RuntimeException exception) {
            log.error("Extraction session {} failed.", sessionId, exception);
            botActionLogService.log(session, new BotAction(BotActionType.FINISH, null, null,
                "Session failed: " + exception.getMessage()), false);
            extractionSessionService.finishExecution(sessionId, executionNumber, false);
        } finally {
            socialNetworkBot.shutdown();
        }
    }
    private void requireRunning(Long sessionId, long executionNumber) {
        // Runs after every bot action, so it is one indexed lookup, not an entity load.
        if (!extractionSessionService.isRunning(sessionId, executionNumber)) {
            throw new SessionPausedException();
        }
    }

    private static class SessionPausedException extends RuntimeException {
    }

}
