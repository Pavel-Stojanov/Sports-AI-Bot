package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vezilka's verdict on a single donated item. Moderation is automatic and
 * synchronous, so this is the final answer — there is no review queue to poll.
 *
 * @param id              Vezilka's identifier of the donation, usable for read-back
 * @param status          {@code accepted} or {@code rejected}
 * @param contentType     {@code text} for this integration
 * @param deduped         true when the same content was already donated; the item
 *                        is <em>not</em> a failure and must not be resent
 * @param awardedPoints   always 0 for API-key donations
 * @param rejectionReason why the item was rejected, e.g. {@code not_macedonian},
 *                        {@code text_too_short}, {@code text_too_long},
 *                        {@code missing_source_url}, {@code conflict}; null when accepted
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DonationItemResult(
    String id,
    String status,
    String contentType,
    boolean deduped,
    Integer awardedPoints,
    String rejectionReason
) {
    private static final String ACCEPTED = "accepted";

    /**
     * Whether the content ended up in the corpus. A deduped item counts as
     * accepted — the content is already there and resending it is pointless.
     */
    public boolean isAccepted() {
        return deduped || ACCEPTED.equalsIgnoreCase(status);
    }

    /**
     * Whether the item carries a verdict the service can store. A deduped item
     * needs no id (nothing new was created); an accepted one must name the
     * record it created. Unknown statuses are kept and stored as rejections,
     * because resending them would only spend the rate limit.
     */
    public boolean isWellFormed() {
        if (deduped) {
            return true;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return !ACCEPTED.equalsIgnoreCase(status) || (id != null && !id.isBlank());
    }

    /** The reason to store for a non-accepted item; never null. */
    public String describeRejection() {
        if (rejectionReason != null && !rejectionReason.isBlank()) {
            return rejectionReason;
        }
        return "rejected".equalsIgnoreCase(status) ? "rejected" : "unexpected status '" + status + "'";
    }
}
