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
}
