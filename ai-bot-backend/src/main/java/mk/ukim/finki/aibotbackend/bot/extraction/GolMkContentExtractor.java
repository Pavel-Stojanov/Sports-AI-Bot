package mk.ukim.finki.aibotbackend.bot.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.bot.llm.LlmClient;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import org.springframework.stereotype.Component;

/**
 * LLM-based extractor for gol.mk pages: one completion call extracts every
 * sports article/result on the page AND writes its 2-3 sentence Macedonian
 * summary (the sports variant's headline feature). The snapshot text is
 * link-annotated, so the model can report each item's absolute sourceUrl.
 */
@Component
@Slf4j
public class GolMkContentExtractor implements ContentExtractor {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String EXTRACTION_SYSTEM_PROMPT = """
        You extract structured sports content from pages of gol.mk, a
        Macedonian sports portal. From the page text you receive, extract every
        distinct sports article or match report that has real editorial text
        (at least a few sentences). Skip navigation, ads, non-sport items, and
        bare fixture or score rows like "Вардар 10:00 Пелистер" — a scoreboard
        line alone is NOT an article. Reply with ONLY a JSON array (no other
        text); each element:
        {"title": "the headline in Macedonian",
         "content": "the full available Macedonian body text, WITHOUT repeating the title",
         "summary": "2-3 full sentences in Macedonian describing what happened
                     (who won, the score, the key moments) — never just a
                     restatement of the team names",
         "sourceUrl": "absolute URL of the item from [brackets], or null",
         "postedAt": "ISO date or date-time if shown, else null"}
        If the page is a listing/scoreboard with no full article text, reply [].
        """;

    private final LlmClient llmClient;

    public GolMkContentExtractor(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public List<CreateExtractedPostDto> extract(PageSnapshot snapshot) {
        if (snapshot.domContent() == null || snapshot.domContent().isBlank()) {
            return List.of();
        }
        try {
            String raw = llmClient.complete(EXTRACTION_SYSTEM_PROMPT,
                "Page URL: %s%nPage title: %s%n%n%s"
                    .formatted(snapshot.url(), snapshot.title(), snapshot.domContent()));
            return parseItems(raw, snapshot.url());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.warn("Could not parse extraction result for {}: {}", snapshot.url(), exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("LLM client error while extracting from {}: {}", snapshot.url(), exception.getMessage());
            return List.of();
        }
    }

    static List<CreateExtractedPostDto> parseItems(String raw, String pageUrl) throws JsonProcessingException {
        JsonNode root = MAPPER.readTree(stripFences(raw));
        if (!root.isArray()) {
            throw new IllegalArgumentException("Expected a JSON array, got: " + root.getNodeType());
        }
        List<CreateExtractedPostDto> posts = new ArrayList<>();
        for (JsonNode item : root) {
            String title = item.path("title").asText("");
            String content = item.path("content").asText("");
            if (title.isBlank() && content.isBlank()) {
                continue;
            }
            String sourceUrl = item.hasNonNull("sourceUrl") ? item.get("sourceUrl").asText() : pageUrl;
            posts.add(new CreateExtractedPostDto(
                slugOf(sourceUrl),
                "gol.mk",
                title.isBlank() ? content : title + "\n\n" + content,
                item.path("summary").asText(null),
                sourceUrl,
                parsePostedAt(item.path("postedAt").asText(null)),
                null,
                List.of()
            ));
        }
        return posts;
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

    private static String slugOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String withoutQuery = url.split("[?#]")[0];
        String[] segments = withoutQuery.split("/");
        return segments.length == 0 ? null : segments[segments.length - 1];
    }

    private static LocalDateTime parsePostedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay();
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }
}
