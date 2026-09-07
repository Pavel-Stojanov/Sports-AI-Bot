package mk.ukim.finki.aibotbackend.integration.vezilka;

import java.util.List;

/**
 * The wire envelope of a text donation towards doniraj.vezilka.ai — the
 * {@code {"items": [...]}} body the API expects. The same shape carries one
 * item and many, up to {@link VezilkaClient#MAX_ITEMS_PER_REQUEST}.
 *
 * @param items the donated items, in the order their results come back
 */
public record TextDonationEnvelope(
    List<TextDonationItem> items
) {
}
