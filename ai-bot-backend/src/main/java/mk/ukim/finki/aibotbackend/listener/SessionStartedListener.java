package mk.ukim.finki.aibotbackend.listener;

import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.core.BotOrchestrator;
import mk.ukim.finki.aibotbackend.events.SessionStartedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the bot asynchronously once a session start has been committed,
 * so the HTTP request that started the session returns immediately.
 */
@Component
@Slf4j
public class SessionStartedListener {
    private final BotOrchestrator botOrchestrator;

    public SessionStartedListener(BotOrchestrator botOrchestrator) {
        this.botOrchestrator = botOrchestrator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("botExecutor")
    public void onSessionStarted(SessionStartedEvent event) {
        log.info("[ASYNC - thread: {}] Running bot for extraction session {}.",
            Thread.currentThread().getName(), event.sessionId());
        botOrchestrator.runSession(event.sessionId(), event.executionNumber());
    }
}
