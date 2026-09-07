package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One text item inside a donation request towards
 * {@code POST /api/public/v1/donations/text/}.
 *
 * <p>The wire format is snake_case, so every component is mapped explicitly.
 * Optional fields are omitted from the payload when null.</p>
 *
 * @param sourceUrl   required — the page the text comes from
 * @param text        the donated Macedonian text; under 20 characters after
 *                    normalisation it is rejected as {@code text_too_short},
 *                    over 100.000 as {@code text_too_long}
 * @param pageTitle   the title of the source page
 * @param pageLanguage the declared language, e.g. {@code mk} — informative only
 * @param retrievedAt ISO-8601 instant at which the text was retrieved
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextDonationItem(
    @JsonProperty("source_url") String sourceUrl,
    @JsonProperty("text") String text,
    @JsonProperty("page_title") String pageTitle,
    @JsonProperty("page_language") String pageLanguage,
    @JsonProperty("retrieved_at") String retrievedAt
) {
}
