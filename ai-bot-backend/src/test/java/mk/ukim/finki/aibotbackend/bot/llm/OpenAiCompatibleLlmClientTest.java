package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenAiCompatibleLlmClientTest {

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
