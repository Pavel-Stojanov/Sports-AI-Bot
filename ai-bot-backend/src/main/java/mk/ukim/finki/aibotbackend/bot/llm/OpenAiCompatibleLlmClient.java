package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * LlmClient against any OpenAI-compatible chat-completions API. The provider
 * is chosen purely by configuration ({@code llm.base-url}, {@code llm.model},
 * {@code llm.api-key}) — no provider-specific code.
 */
@Component
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 30_000L;
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000L;
    private static final long RETRY_DELAY_MARGIN_MILLIS = 1_000L;
    private static final Pattern TRY_AGAIN_PATTERN =
        Pattern.compile("try again in ([0-9.]+)s", Pattern.CASE_INSENSITIVE);

    private static final String DECISION_SYSTEM_PROMPT = """
        You control a browser agent extracting Macedonian sports content from the
        public sports portal gol.mk. On every turn you see the current page and
        the goal, and you choose exactly ONE next action.

        Allowed action types:
        NAVIGATE (target = absolute URL), CLICK (target = CSS selector or exact
        visible text), TYPE (target = selector/text, value = text to type),
        SCROLL, WAIT, EXTRACT (target = the current page URL; extracts the sports
        content of the current page),
        FINISH (stop working on this goal).

        Rules:
        - If the goal names a starting URL and it does not appear in the history yet,
          your first action is NAVIGATE to it.
        - Prefer NAVIGATE using the absolute URLs shown in [brackets] in the page text.
        - Never NAVIGATE to a URL that already appears in the history.
        - Listing and scoreboard pages (fixture tables, section fronts like /rezultati) are
          for FINDING links only — never EXTRACT them; NAVIGATE into an individual match
          report or article linked from them instead.
        - EXTRACT only when the current page shows ONE full article or match report.
        - Do not EXTRACT the same URL twice; check the EXTRACT target URLs in the history.
        - The site is public: never use LOGIN.
        - After 3 successful EXTRACTs (within the usual 3-5 articles), use FINISH.
        - Never FINISH before your first EXTRACT — a listing page with article links in
          [brackets] always leaves you a NAVIGATE move. FINISH early only if the history
          shows the same action failing repeatedly.
        - goalReached refers ONLY to the current goal: it is true only once the history
          shows the EXTRACTs this goal asked for. When the history is empty, goalReached
          is always false — whatever the page shows, the goal's work has not started yet.

        Respond with ONLY this JSON object, no other text:
        {"action": {"type": "...", "target": "... or null", "value": "... or null",
         "reasoning": "one short sentence"},
         "goalReached": true/false, "rationale": "one short sentence"}
        """;

    private final RestClient restClient;
    private final LlmProperties llmProperties;

    public OpenAiCompatibleLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        this.restClient = RestClient.builder()
            .baseUrl(llmProperties.baseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmProperties.apiKey())
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
            "model", llmProperties.model(),
            "temperature", 0.2,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            )
        );
        int retries = 0;
        while (true) {
            try {
                JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
                return response.path("choices").path(0).path("message").path("content").asText();
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != HttpStatus.TOO_MANY_REQUESTS.value()
                    || retries >= MAX_RATE_LIMIT_RETRIES) {
                    throw new BotExecutionException("LLM call failed: " + exception.getMessage(), exception);
                }
                retries++;
                long delayMillis = retryDelayMillis(
                    exception.getResponseHeaders() != null
                        ? exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER) : null,
                    exception.getResponseBodyAsString());
                log.warn("LLM rate limited (429); waiting {}ms before retry {}/{}",
                    delayMillis, retries, MAX_RATE_LIMIT_RETRIES);
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new BotExecutionException(
                        "LLM call interrupted while waiting to retry after rate limit", interruptedException);
                }
            } catch (RestClientException exception) {
                throw new BotExecutionException("LLM call failed: " + exception.getMessage(), exception);
            }
        }
    }

    static long retryDelayMillis(String retryAfterHeader, String responseBody) {
        Long headerSeconds = parseSeconds(retryAfterHeader);
        if (headerSeconds != null) {
            return capRetryDelayMillis(headerSeconds * 1000 + RETRY_DELAY_MARGIN_MILLIS);
        }
        if (responseBody != null) {
            Matcher matcher = TRY_AGAIN_PATTERN.matcher(responseBody);
            if (matcher.find()) {
                try {
                    double seconds = Double.parseDouble(matcher.group(1));
                    return capRetryDelayMillis((long) (seconds * 1000) + RETRY_DELAY_MARGIN_MILLIS);
                } catch (NumberFormatException ignored) {
                    // fall through to default
                }
            }
        }
        return DEFAULT_RETRY_DELAY_MILLIS;
    }

    private static Long parseSeconds(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static long capRetryDelayMillis(long millis) {
        return Math.min(millis, MAX_RETRY_DELAY_MILLIS);
    }

    @Override
    public BotDecision decideNextAction(PageSnapshot snapshot, String goal, List<BotAction> history) {
        String userPrompt = buildDecisionPrompt(snapshot, goal, history);
        String raw = complete(DECISION_SYSTEM_PROMPT, userPrompt);
        try {
            return guardFirstDecision(parseDecision(raw), history);
        } catch (JsonProcessingException | IllegalArgumentException firstError) {
            log.warn("Malformed LLM decision, retrying once: {}", firstError.getMessage());
            String retryRaw = complete(DECISION_SYSTEM_PROMPT,
                userPrompt + "\n\nYour previous reply was not the required JSON ("
                    + firstError.getMessage() + "). Reply with ONLY the JSON object.");
            try {
                return guardFirstDecision(parseDecision(retryRaw), history);
            } catch (JsonProcessingException | IllegalArgumentException secondError) {
                log.error("LLM decision unparseable twice, finishing target: {}", secondError.getMessage());
                return new BotDecision(
                    new BotAction(BotActionType.FINISH, null, null,
                        "LLM output was not parseable twice in a row"),
                    true,
                    "Safe FINISH fallback after two malformed LLM responses.");
            }
        }
    }

    /**
     * The agentic loop stops a target as soon as {@code goalReached} is true —
     * before performing or logging the decision's action. A spurious
     * {@code goalReached: true} on the FIRST decision of a target therefore
     * skips the whole target silently (observed in live runs), so it is
     * deterministically overridden here.
     */
    static BotDecision guardFirstDecision(BotDecision decision, List<BotAction> history) {
        if (decision.goalReached() && (history == null || history.isEmpty())) {
            log.warn("LLM claimed goalReached on the first decision of a target — overriding to false.");
            return new BotDecision(decision.action(), false, decision.rationale());
        }
        return decision;
    }

    static BotDecision parseDecision(String raw) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(stripFences(raw));
        JsonNode action = root.path("action");
        BotAction botAction = new BotAction(
            BotActionType.valueOf(action.path("type").asText("FINISH")),
            action.hasNonNull("target") ? action.get("target").asText() : null,
            action.hasNonNull("value") ? action.get("value").asText() : null,
            action.path("reasoning").asText("")
        );
        return new BotDecision(
            botAction,
            root.path("goalReached").asBoolean(false),
            root.path("rationale").asText("")
        );
    }

    private static String stripFences(String raw) {
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return trimmed;
    }

    private String buildDecisionPrompt(PageSnapshot snapshot, String goal, List<BotAction> history) {
        StringBuilder historyText = new StringBuilder();
        history.stream()
            .skip(Math.max(0, history.size() - 15))
            .forEach(action -> historyText
                .append("- ").append(action.type())
                .append(action.target() != null ? " " + action.target() : "")
                .append('\n'));
        return """
            GOAL: %s

            CURRENT PAGE
            URL: %s
            Title: %s
            Text:
            %s

            ACTIONS SO FAR (oldest first):
            %s""".formatted(goal, snapshot.url(), snapshot.title(), snapshot.domContent(),
            historyText.isEmpty() ? "(none)" : historyText.toString());
    }
}
