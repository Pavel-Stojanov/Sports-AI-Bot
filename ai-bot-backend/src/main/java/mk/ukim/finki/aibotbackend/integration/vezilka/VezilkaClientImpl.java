package mk.ukim.finki.aibotbackend.integration.vezilka;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client towards the Vezilka Public Donation API v1.
 *
 * <p>Authentication is the {@code X-Donation-Api-Key} header. The Bearer form
 * documented alongside it belongs to logged-in Vezilka users, carries points,
 * and is deliberately not used here — a server-side key is the right identity
 * for a bot, and the media endpoints reject anything else.</p>
 */
@Component
public class VezilkaClientImpl implements BatchVezilkaClient {
    private static final String TEXT_DONATIONS_PATH = "/api/public/v1/donations/text/";
    private static final String DONATION_PATH = "/api/public/v1/donations/{id}/";
    private static final String API_KEY_HEADER = "X-Donation-Api-Key";

    private static final long DEFAULT_RETRY_SECONDS = 60;

    private final RestClient restClient;

    public VezilkaClientImpl(VezilkaProperties vezilkaProperties, ClientHttpRequestFactory requestFactory) {
        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(vezilkaProperties.baseUrl())
            .defaultHeader(API_KEY_HEADER, vezilkaProperties.apiKey())
            .build();
    }

    @Override
    public DonationReceipt submitTextDonation(TextDonationRequest request) {
        TextDonationItem item = new TextDonationItem(
            request.sourceUrl(), request.content(), request.title(), null, null);
        DonationItemResult result = submitTextDonations(List.of(item)).results().getFirst();
        return new DonationReceipt(
            result.id(),
            result.isAccepted() ? "accepted" : result.rejectionReason());
    }

    @Override
    public DonationResponse submitTextDonations(List<TextDonationItem> items) {
        if (items.isEmpty() || items.size() > MAX_ITEMS_PER_REQUEST) {
            throw new VezilkaIntegrationException(
                "A donation request carries between 1 and %d items, got %d."
                    .formatted(MAX_ITEMS_PER_REQUEST, items.size()));
        }
        try {
            // Both 201 (something was accepted) and 200 (everything was rejected)
            // are valid answers; the verdict is read per item from the body.
            DonationResponse response = restClient.post()
                .uri(TEXT_DONATIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new TextDonationEnvelope(items))
                .retrieve()
                .body(DonationResponse.class);
            validateResponse(response, items.size());
            return response;
        } catch (HttpStatusCodeException exception) {
            throw new VezilkaIntegrationException(describe(exception), exception, retryAt(exception));
        } catch (RestClientException exception) {
            // No HTTP answer at all: timeout, reset, DNS. Worth another try later.
            throw new VezilkaIntegrationException(
                "Donating %d items to Vezilka failed: %s"
                    .formatted(items.size(), exception.getMessage()), exception,
                Instant.now().plusSeconds(DEFAULT_RETRY_SECONDS));
        }
    }

    @Override
    public DonationStatus checkStatus(String vezilkaReference) {
        try {
            DonationItemResult donation = restClient.get()
                .uri(DONATION_PATH, vezilkaReference)
                .retrieve()
                .body(DonationItemResult.class);
            if (donation == null || donation.status() == null) {
                throw new VezilkaIntegrationException("Vezilka returned an empty or invalid donation status.");
            }
            return donation.isAccepted() ? DonationStatus.ACCEPTED : DonationStatus.REJECTED;
        } catch (HttpStatusCodeException exception) {
            throw new VezilkaIntegrationException(
                "Reading donation '%s' back from Vezilka failed: %s"
                    .formatted(vezilkaReference, describe(exception)), exception);
        } catch (RestClientException exception) {
            throw new VezilkaIntegrationException(
                "Reading donation '%s' back from Vezilka failed: %s"
                    .formatted(vezilkaReference, exception.getMessage()), exception);
        }
    }

    private void validateResponse(DonationResponse response, int expected) {
        if (response == null || response.results() == null || response.results().size() != expected
            || response.results().stream().anyMatch(item -> item == null || !item.isWellFormed())) {
            throw new VezilkaIntegrationException("Vezilka returned an incomplete or invalid donation response.");
        }
    }

    /** Null for a 4xx other than 429: the request itself is wrong, so sending it again cannot help. */
    private Instant retryAt(HttpStatusCodeException exception) {
        if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS
            && !exception.getStatusCode().is5xxServerError()) {
            return null;
        }
        String value = exception.getResponseHeaders() == null ? null
            : exception.getResponseHeaders().getFirst("Retry-After");
        if (value != null) {
            try {
                return Instant.now().plusSeconds(Math.max(0, Long.parseLong(value.trim())));
            } catch (NumberFormatException ignored) {
                try {
                    return ZonedDateTime.parse(value,
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (DateTimeParseException invalidDate) {
                    // Use the default retry delay when the header is invalid.
                }
            }
        }
        return Instant.now().plusSeconds(DEFAULT_RETRY_SECONDS);
    }

    private String describe(HttpStatusCodeException exception) {
        if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            String retryAfter = exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst("Retry-After");
            return "Vezilka rate limit exceeded, retry after %s seconds."
                .formatted(retryAfter == null ? "the advertised delay" : retryAfter);
        }
        return "Vezilka answered %s: %s"
            .formatted(exception.getStatusCode(), exception.getResponseBodyAsString());
    }
}
