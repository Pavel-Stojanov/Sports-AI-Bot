package mk.ukim.finki.aibotbackend.model.exception;

import java.time.Instant;

/**
 * Thrown by the {@code VezilkaClient} when communication with
 * doniraj.vezilka.ai fails.
 */
public class VezilkaIntegrationException extends RuntimeException {
    /** When the call may be repeated; null when repeating it cannot help. */
    private final Instant retryAt;

    public VezilkaIntegrationException(String message, Throwable cause, Instant retryAt) {
        super(message, cause);
        this.retryAt = retryAt;
    }

    /** A failure that a retry cannot fix: a malformed answer or a request the API refused. */
    public VezilkaIntegrationException(String message) {
        this(message, null, null);
    }

    public VezilkaIntegrationException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public Instant getRetryAt() {
        return retryAt;
    }

    public boolean isRetryable() {
        return retryAt != null;
    }
}
