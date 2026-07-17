package mk.ukim.finki.aibotbackend.bot.core;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.exception.SessionNotFoundException;
import mk.ukim.finki.aibotbackend.service.domain.BotActionLogService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import mk.ukim.finki.aibotbackend.service.domain.ExtractionSessionService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotOrchestratorImpl implements BotOrchestrator {
    private final SocialNetworkBot socialNetworkBot;
    private final ExtractionSessionService extractionSessionService;
    private final ExtractedPostService extractedPostService;
    private final BotActionLogService botActionLogService;

    public BotOrchestratorImpl(
        SocialNetworkBot socialNetworkBot,
        ExtractionSessionService extractionSessionService,
        ExtractedPostService extractedPostService,
        BotActionLogService botActionLogService
    ) {
        this.socialNetworkBot = socialNetworkBot;
        this.extractionSessionService = extractionSessionService;
        this.extractedPostService = extractedPostService;
        this.botActionLogService = botActionLogService;
    }

    @Override
    public void runSession(Long sessionId) {
        ExtractionSession session = extractionSessionService
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

        try {
            socialNetworkBot.login();
            for (ExtractionTarget target : session.getTargets()) {
                List<CreateExtractedPostDto> extracted = socialNetworkBot.execute(
                    target,
                    (action, successful) -> botActionLogService.log(session, action, successful));
                extractedPostService.saveAll(
                    extracted.stream()
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
