package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What doniraj.vezilka.ai returns for a donation request: one result per sent
 * item, in the order the items were sent, plus the totals.
 *
 * <p>The HTTP code alone says nothing about success — 200 means the request was
 * valid but <em>every</em> item was rejected. The decision lives in
 * {@link #results()}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DonationResponse(
    List<DonationItemResult> results,
    int total,
    int accepted,
    int rejected,
    int duplicates
) {
}
