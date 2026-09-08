package mk.ukim.finki.aibotbackend.integration.vezilka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import mk.ukim.finki.aibotbackend.config.HttpClientConfig;
import mk.ukim.finki.aibotbackend.model.enums.DonationStatus;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VezilkaClientImplTest {
    private HttpServer server;
    private VezilkaClientImpl client;
    private int status = 201;
    private String response = """
        {"results":[{"id":"one","status":"accepted","contentType":"text","deduped":false}],
         "total":1,"accepted":1,"rejected":0,"duplicates":0}
        """;
    private String retryAfter;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("X-Donation-Api-Key"));
            method.set(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = new VezilkaClientImpl(new VezilkaProperties(
            "http://127.0.0.1:" + server.getAddress().getPort(), "test-key"), HttpClientConfig.requestFactory(1_000, 5_000));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private DonationResponse donate() {
        return client.submitTextDonations(List.of(new TextDonationItem(
            "https://www.gol.mk/fudbal/test", "Вардар победи во натпреварот.", null, "mk", null)));
    }

    @Test
    void sendsDocumentedEnvelopeAndApiKey() throws Exception {
        assertThat(donate().results().getFirst().isAccepted()).isTrue();
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/public/v1/donations/text/");
        assertThat(key.get()).isEqualTo("test-key");
        var item = new ObjectMapper().readTree(body.get()).path("items").get(0);
        assertThat(item.path("source_url").asText()).isEqualTo("https://www.gol.mk/fudbal/test");
        assertThat(item.path("text").asText()).isEqualTo("Вардар победи во натпреварот.");
        assertThat(item.has("retrieved_at")).isFalse();
    }

    @Test
    void http200CanMeanRejectedAndRejectedItemsCanHaveNoId() {
        status = 200;
        response = """
            {"results":[{"id":null,"status":"rejected","rejectionReason":"not_macedonian"}],
             "total":1,"accepted":0,"rejected":1,"duplicates":0}
            """;
        assertThat(donate().results().getFirst().isAccepted()).isFalse();
    }

    @Test
    void readsStatusThroughTheOriginalInterface() {
        response = "{\"id\":\"one\",\"status\":\"accepted\"}";
        VezilkaClient originalContract = client;
        assertThat(originalContract.checkStatus("one")).isEqualTo(DonationStatus.ACCEPTED);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(path.get()).isEqualTo("/api/public/v1/donations/one/");
    }

    @Test
    void preservesTheServerRetryDelay() {
        status = 429;
        retryAfter = "120";
        Instant earliest = Instant.now().plusSeconds(120);
        assertThatThrownBy(this::donate).isInstanceOfSatisfying(VezilkaIntegrationException.class,
            error -> assertThat(error.getRetryAt()).isAfterOrEqualTo(earliest));
    }

    @Test
    void supportsAnHttpDateRetryHeader() {
        status = 429;
        retryAfter = "Wed, 01 Jan 2031 00:00:00 GMT";
        assertThatThrownBy(this::donate).isInstanceOfSatisfying(VezilkaIntegrationException.class,
            error -> assertThat(error.getRetryAt()).isEqualTo(Instant.parse("2031-01-01T00:00:00Z")));
    }

    @Test
    void refusedRequestsAreNotRetryable() {
        status = 401;
        response = "{\"detail\":\"invalid key\"}";
        assertThatThrownBy(this::donate).isInstanceOfSatisfying(VezilkaIntegrationException.class,
            error -> assertThat(error.isRetryable()).isFalse());
    }

    @Test
    void serverErrorsAreRetryable() {
        status = 503;
        response = "{}";
        assertThatThrownBy(this::donate).isInstanceOfSatisfying(VezilkaIntegrationException.class,
            error -> assertThat(error.isRetryable()).isTrue());
    }

    @Test
    void malformedAnswersAreNotRetryable() {
        response = "{}";
        assertThatThrownBy(this::donate).isInstanceOfSatisfying(VezilkaIntegrationException.class,
            error -> assertThat(error.isRetryable()).isFalse());
    }

    @Test
    void dedupedItemsNeedNoId() {
        response = """
            {"results":[{"id":null,"status":"accepted","contentType":"text","deduped":true}],
             "total":1,"accepted":0,"rejected":0,"duplicates":1}
            """;
        assertThat(donate().results().getFirst().isAccepted()).isTrue();
    }

    @Test
    void unknownStatusesAreKeptAsFinalRejections() {
        status = 200;
        response = """
            {"results":[{"id":"one","status":"quarantined","contentType":"text","deduped":false}],
             "total":1,"accepted":0,"rejected":1,"duplicates":0}
            """;
        DonationItemResult result = donate().results().getFirst();
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.describeRejection()).isEqualTo("unexpected status 'quarantined'");
    }

    @Test
    void acceptedItemsWithoutAnIdAreInvalid() {
        response = """
            {"results":[{"id":null,"status":"accepted","contentType":"text","deduped":false}],
             "total":1,"accepted":1,"rejected":0,"duplicates":0}
            """;
        assertThatThrownBy(this::donate).isInstanceOf(VezilkaIntegrationException.class);
    }

    @Test
    void rejectsMissingItemResults() {
        response = "{}";
        assertThatThrownBy(this::donate).isInstanceOf(VezilkaIntegrationException.class);
    }

    @Test
    void rejectsEmptySuccessResponses() {
        status = 204;
        response = "";
        assertThatThrownBy(this::donate).isInstanceOf(VezilkaIntegrationException.class);
    }
}
