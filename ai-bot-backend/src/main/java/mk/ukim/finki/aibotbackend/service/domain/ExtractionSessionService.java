package mk.ukim.finki.aibotbackend.service.domain;

import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionSession;

/**
 * Domain service for extraction sessions. Works with entities only —
 * DTO mapping happens one layer above, in the application service.
 */
public interface ExtractionSessionService {
    List<ExtractionSession> findAll();

    Optional<ExtractionSession> findById(Long id);

    ExtractionSession create(ExtractionSession session);

    /**
     * Transitions a CREATED or PAUSED session to RUNNING and stamps
     * {@code startedAt}. Throws {@code SessionNotFoundException} or
     * {@code InvalidSessionStateException} accordingly.
     */
    ExtractionSession start(Long id);

    /**
     * Transitions a RUNNING session to PAUSED.
     */
    ExtractionSession stop(Long id);

    /**
     * Transitions a RUNNING session to COMPLETED and stamps {@code finishedAt}.
     */
    ExtractionSession complete(Long id);

    /**
     * Transitions a session to FAILED and stamps {@code finishedAt}.
     */
    ExtractionSession fail(Long id);

    void finishExecution(Long id, long executionNumber, boolean successful);

    /** Whether the session is RUNNING under exactly this execution number. */
    boolean isRunning(Long id, long executionNumber);
}
