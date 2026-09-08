package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.config.HttpClientConfig;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenAiCompatibleLlmClientTest {

    private static final String COMPLETION =
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}";

    /** Answers the given HTTP statuses in order; the last one repeats. */
    private static HttpServer server(AtomicInteger requests, int... statuses) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int index = Math.min(requests.getAndIncrement(), statuses.length - 1);
            if (statuses[index] == 204) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            byte[] bytes = (statuses[index] == 200 ? COMPLETION : "{\"error\":\"down\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statuses[index], bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void providerOutagesAreRetriedBeforeFailing() throws Exception {
        var requests = new AtomicInteger();
        var server = server(requests, 503, 200);
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test", "test"), HttpClientConfig.requestFactory(1_000, 5_000));
            assertThat(client.complete("system", "user")).isEqualTo("ok");
            assertThat(requests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientErrorsAreNotRetried() throws Exception {
        var requests = new AtomicInteger();
        var server = server(requests, 400);
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test", "test"), HttpClientConfig.requestFactory(1_000, 5_000));
            assertThatThrownBy(() -> client.complete("system", "user"))
                .isInstanceOf(BotExecutionException.class);
            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emptyBodiesAreRetriedLikeTimeouts() throws Exception {
        var requests = new AtomicInteger();
        var server = server(requests, 204, 200);
        try {
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(new LlmProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test", "test"), HttpClientConfig.requestFactory(1_000, 5_000));
            assertThat(client.complete("system", "user")).isEqualTo("ok");
            assertThat(requests).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedResponsesEndTheTargetWithFinishAfterOneRepairAttempt() {
        var calls = new AtomicInteger();
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
            new LlmProperties("http://127.0.0.1", "test", "test"), HttpClientConfig.requestFactory(1_000, 5_000)) {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                calls.incrementAndGet();
                return "{}";
            }
        };
        BotDecision decision = client.decideNextAction(
            new PageSnapshot("https://www.gol.mk/", "Sports", "text", null), "Extract articles", List.of());
        assertThat(decision.action().type()).isEqualTo(BotActionType.FINISH);
        assertThat(decision.goalReached()).isFalse();
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsMissingDecisionFields() {
        assertThatThrownBy(() -> OpenAiCompatibleLlmClient.parseDecision("{}"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenAiCompatibleLlmClient.parseDecision(
            "{\"action\": {\"type\": \"NAVIGATE\"}, \"goalReached\": false}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesAPlainJsonDecision() throws Exception {
        BotDecision decision = OpenAiCompatibleLlmClient.parseDecision("""
            {"action": {"type": "NAVIGATE", "target": "https://www.gol.mk/rezultati",
             "value": null, "reasoning": "results page"},
             "goalReached": false, "rationale": "need results"}""");

        assertThat(decision.goalReached()).isFalse();
        assertThat(decision.action().type()).isEqualTo(BotActionType.NAVIGATE);
        assertThat(decision.action().target()).isEqualTo("https://www.gol.mk/rezultati");
    }

    @Test
    void firstDecisionOfATargetCannotClaimGoalReached() {
        BotDecision premature = new BotDecision(
            new BotAction(BotActionType.FINISH, null, null, "done"), true, "already satisfied");

        BotDecision guarded = OpenAiCompatibleLlmClient.guardFirstDecision(premature, List.of());

        assertThat(guarded.goalReached()).isFalse();
        assertThat(guarded.action().type()).isEqualTo(BotActionType.FINISH);
    }

    @Test
    void goalReachedSurvivesWhenHistoryExists() {
        BotDecision legitimate = new BotDecision(
            new BotAction(BotActionType.FINISH, null, null, "done"), true, "3 extracts done");
        List<BotAction> history = List.of(
            new BotAction(BotActionType.EXTRACT, "https://www.gol.mk/x", null, ""));

        assertThat(OpenAiCompatibleLlmClient.guardFirstDecision(legitimate, history).goalReached())
            .isTrue();
    }

    @Test
    void parsesAFencedJsonDecision() throws Exception {
        BotDecision decision = OpenAiCompatibleLlmClient.parseDecision(
            "```json\n{\"action\": {\"type\": \"EXTRACT\"}, \"goalReached\": false, \"rationale\": \"r\"}\n```");
        assertThat(decision.action().type()).isEqualTo(BotActionType.EXTRACT);
    }

    @Test
    void throwsOnGarbage() {
        assertThatThrownBy(() -> OpenAiCompatibleLlmClient.parseDecision("I think we should click around"))
            .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void throwsOnUnknownActionType() {
        assertThatThrownBy(() -> OpenAiCompatibleLlmClient.parseDecision(
            "{\"action\": {\"type\": \"FLY\"}, \"goalReached\": false}"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryDelayPrefersRetryAfterHeaderOverBody() {
        long delayMillis = OpenAiCompatibleLlmClient.retryDelayMillis(
            "5", "Please try again in 24.07s");
        assertThat(delayMillis).isEqualTo(6_000L);
    }

    @Test
    void retryDelayParsesTryAgainInFromBody() {
        long delayMillis = OpenAiCompatibleLlmClient.retryDelayMillis(
            null, "rate_limit_exceeded: Please try again in 24.07s.");
        assertThat(delayMillis).isEqualTo(25_070L);
    }

    @Test
    void retryDelayDefaultsTo30sOnGarbageOrNulls() {
        assertThat(OpenAiCompatibleLlmClient.retryDelayMillis(null, null)).isEqualTo(30_000L);
        assertThat(OpenAiCompatibleLlmClient.retryDelayMillis("not-a-number", "no useful info here"))
            .isEqualTo(30_000L);
    }

    @Test
    void retryDelayIsCappedAt60s() {
        assertThat(OpenAiCompatibleLlmClient.retryDelayMillis("120", null)).isEqualTo(60_000L);
        assertThat(OpenAiCompatibleLlmClient.retryDelayMillis(null, "try again in 90s"))
            .isEqualTo(60_000L);
    }
}
