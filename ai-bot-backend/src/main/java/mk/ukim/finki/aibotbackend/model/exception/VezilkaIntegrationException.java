package mk.ukim.finki.aibotbackend.model.exception;

import java.time.Instant;

/**
 * Thrown by the {@code VezilkaClient} when communication with
 * doniraj.vezilka.ai fails.
 */
public class VezilkaIntegrationException extends RuntimeException {
    private final Instant retryAt;

    public Instant getRetryAt() {
        return retryAt;
    }

    public VezilkaIntegrationException(String message, Throwable cause, Instant retryAt) {
        super(message, cause);
        this.retryAt = retryAt;
    }
    public VezilkaIntegrationException(String message) {
        this(message, null, Instant.now().plusSeconds(60));
    }

    public VezilkaIntegrationException(String message, Throwable cause) {
        this(message, cause, Instant.now().plusSeconds(60));
    }
}
