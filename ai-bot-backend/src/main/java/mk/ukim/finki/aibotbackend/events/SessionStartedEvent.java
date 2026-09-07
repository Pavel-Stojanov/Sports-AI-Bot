package mk.ukim.finki.aibotbackend.events;

/**
 * Published after an extraction session is started via the API.
 * Handled asynchronously by {@code SessionStartedListener}, which kicks
 * off the bot run outside of the web request.
 */
public record SessionStartedEvent(Long sessionId, long executionNumber) {
}
