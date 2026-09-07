package mk.ukim.finki.aibotbackend.integration.vezilka;

import java.util.List;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;

/**
 * The integration seam towards <a href="https://doniraj.vezilka.ai">doniraj.vezilka.ai</a>.
 *
 * <p>TODO(student): Provide an implementation using the submission mechanism
 * agreed with the professor (HTTP API or automated form submission), configured
 * through {@link VezilkaProperties}. Throw
 * {@code VezilkaIntegrationException} when communication fails.</p>
 *
 * <p>Implemented against the Public Donation API v1. Moderation on the Vezilka
 * side is automatic and synchronous: a donation response already carries the
 * final verdict for every item it answers, so {@link #checkStatus(String)}
 * confirms a recorded verdict rather than waiting for one — see
 * {@code DonationService.refreshSubmittedStatuses()} for when that matters.</p>
 */
public interface VezilkaClient {
    /**
     * The largest number of items the API accepts in one request. More than
     * this is answered with 400.
     */
    int MAX_ITEMS_PER_REQUEST = 100;

    /**
     * Submits one text donation and returns Vezilka's receipt for it.
     */
    DonationReceipt submitTextDonation(TextDonationRequest request);

    /**
     * Checks what happened to a previously submitted donation.
     *
     * @param vezilkaReference the reference from the {@link DonationReceipt}
     */
    DonationStatus checkStatus(String vezilkaReference);

    /**
     * Donates several text items in one request and returns Vezilka's verdict
     * for each of them, in the order they were sent. Batching is what the API
     * asks for: one request per item wastes the hourly rate limit.
     *
     * @param items at most {@link #MAX_ITEMS_PER_REQUEST} items
     * @throws mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException
     *         when the call fails or the batch is oversized
     */
    DonationResponse submitTextDonations(List<TextDonationItem> items);
}
