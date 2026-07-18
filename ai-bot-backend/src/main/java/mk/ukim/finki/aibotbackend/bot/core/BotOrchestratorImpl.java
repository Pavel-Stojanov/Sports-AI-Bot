package mk.ukim.finki.aibotbackend.bot.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
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
    public void runSession(Long sessionId) {
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

        try {
            socialNetworkBot.login();
            // The LLM sometimes EXTRACTs the same page more than once despite the
            // prompt rules, so posts are deduplicated by externalId across the run.
            Set<String> seenExternalIds = new HashSet<>();
            for (ExtractionTarget target : session.getTargets()) {
                List<CreateExtractedPostDto> extracted = socialNetworkBot.execute(
                    target,
                    (action, successful) -> botActionLogService.log(session, action, successful));
                extractedPostService.saveAll(
                    extracted.stream()
                        .filter(dto -> dto.externalId() == null || seenExternalIds.add(dto.externalId()))
                        .map(dto -> dto.toExtractedPost(session))
                        .toList());
            }
            extractionSessionService.complete(sessionId);
        } catch (RuntimeException exception) {
            log.error("Extraction session {} failed.", sessionId, exception);
            extractionSessionService.fail(sessionId);
        } finally {
            socialNetworkBot.shutdown();
        }
    }
}
