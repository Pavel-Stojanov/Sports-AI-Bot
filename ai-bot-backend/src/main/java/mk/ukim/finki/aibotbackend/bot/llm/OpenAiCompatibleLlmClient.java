package mk.ukim.finki.aibotbackend.bot.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.model.enums.BotActionType;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * LlmClient against any OpenAI-compatible chat-completions API. The provider
 * is chosen purely by configuration ({@code llm.base-url}, {@code llm.model},
 * {@code llm.api-key}) — no provider-specific code.
 */
@Component
@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        - Prefer NAVIGATE using the absolute URLs shown in [brackets] in the page text.
        - Use EXTRACT when the current page shows sports results or a full article.
        - Do not EXTRACT the same URL twice; check the EXTRACT target URLs in the history.
        - The site is public: never use LOGIN.
        - After extracting from 3-5 pages, or when nothing relevant is left, use FINISH.

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
        try {
            JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            return response.path("choices").path(0).path("message").path("content").asText();
        } catch (RestClientException exception) {
            throw new BotExecutionException("LLM call failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public BotDecision decideNextAction(PageSnapshot snapshot, String goal, List<BotAction> history) {
        String userPrompt = buildDecisionPrompt(snapshot, goal, history);
        String raw = complete(DECISION_SYSTEM_PROMPT, userPrompt);
        try {
            return parseDecision(raw);
        } catch (JsonProcessingException | IllegalArgumentException firstError) {
            log.warn("Malformed LLM decision, retrying once: {}", firstError.getMessage());
            String retryRaw = complete(DECISION_SYSTEM_PROMPT,
                userPrompt + "\n\nYour previous reply was not the required JSON ("
                    + firstError.getMessage() + "). Reply with ONLY the JSON object.");
            try {
                return parseDecision(retryRaw);
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
