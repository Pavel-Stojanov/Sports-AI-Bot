package mk.ukim.finki.aibotbackend.bot.core;

/**
 * Entry point for running a whole extraction session, called asynchronously
 * by the {@code SessionStartedListener} after a session is started via the API.
 */
public interface BotOrchestrator {
    /**
     * Runs the bot for every target of the given session: logs in, executes
     * the agentic loop per target, persists the extracted posts and the action
     * logs, and finally marks the session COMPLETED or FAILED.
     */
    void runSession(Long sessionId, long executionNumber);
}
