package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.databind.JsonNode;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP client towards doniraj.vezilka.ai.
 *
 * <p>ASSUMED contract (to be confirmed with the professor):
 * POST {base-url}/api/donations with {title, content, sourceUrl} returning
 * {reference, message}; GET {base-url}/api/donations/{reference}/status
 * returning {status}. All requests carry the API key as a Bearer token.
 * If the agreed mechanism differs, only this class changes.</p>
 */
@Component
public class VezilkaClientImpl implements VezilkaClient {
    private final RestClient restClient;

    public VezilkaClientImpl(VezilkaProperties vezilkaProperties) {
        this.restClient = RestClient.builder()
            .baseUrl(vezilkaProperties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + vezilkaProperties.apiKey())
            .build();
    }

    @Override
    public DonationReceipt submitTextDonation(TextDonationRequest request) {
        try {
            JsonNode response = restClient.post()
                .uri("/api/donations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
            return new DonationReceipt(
                response.path("reference").asText(),
                response.path("message").asText(null));
        } catch (RestClientException exception) {
            throw new VezilkaIntegrationException(
                "Submitting the donation to Vezilka failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public DonationStatus checkStatus(String vezilkaReference) {
        try {
            JsonNode response = restClient.get()
                .uri("/api/donations/{reference}/status", vezilkaReference)
                .retrieve()
                .body(JsonNode.class);
            return DonationStatus.valueOf(response.path("status").asText());
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new VezilkaIntegrationException(
                "Checking the Vezilka status of '%s' failed: %s"
                    .formatted(vezilkaReference, exception.getMessage()), exception);
        }
    }
}
