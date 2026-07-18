package mk.ukim.finki.aibotbackend.bot.extraction;

import java.util.List;
import mk.ukim.finki.aibotbackend.bot.browser.PageSnapshot;
import mk.ukim.finki.aibotbackend.bot.llm.BotAction;
import mk.ukim.finki.aibotbackend.bot.llm.BotDecision;
import mk.ukim.finki.aibotbackend.bot.llm.LlmClient;
import mk.ukim.finki.aibotbackend.model.dto.CreateExtractedPostDto;
import mk.ukim.finki.aibotbackend.model.enums.MediaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GolMkContentExtractorTest {
    private static final String CANNED_JSON = """
        [{"title": "Вардар славеше во дербито",
          "content": "Вардар победи со 2:1 против Пелистер во голем натпревар.",
          "summary": "Вардар го доби дербито против Пелистер со 2:1.",
          "sourceUrl": "https://www.gol.mk/fudbal/vardar-slavese-vo-derbito",
          "postedAt": "2026-07-16T20:30:00"}]""";

    private final LlmClient fakeLlm = new LlmClient() {
        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return CANNED_JSON;
        }

        @Override
        public BotDecision decideNextAction(PageSnapshot s, String g, List<BotAction> h) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void extractsPostsWithSummaries() {
        GolMkContentExtractor extractor = new GolMkContentExtractor(fakeLlm);
        List<CreateExtractedPostDto> posts = extractor.extract(
            new PageSnapshot("https://www.gol.mk/fudbal", "Фудбал", "some page text", null));

        assertThat(posts).hasSize(1);
        CreateExtractedPostDto post = posts.getFirst();
        assertThat(post.content()).contains("Вардар славеше во дербито").contains("2:1");
        assertThat(post.summary()).isEqualTo("Вардар го доби дербито против Пелистер со 2:1.");
        assertThat(post.sourceUrl()).isEqualTo("https://www.gol.mk/fudbal/vardar-slavese-vo-derbito");
        assertThat(post.externalId()).isEqualTo("vardar-slavese-vo-derbito");
        assertThat(post.authorHandle()).isEqualTo("gol.mk");
        assertThat(post.macedonianConfidence()).isNull();
        assertThat(post.postedAt()).isNotNull();
    }

    @Test
    void mediaUrlsBecomeImageMediaItems() throws Exception {
        List<CreateExtractedPostDto> posts = GolMkContentExtractor.parseItems("""
            [{"title": "Наслов", "content": "Текст на статијата.",
              "summary": "Резиме.", "sourceUrl": null, "postedAt": null,
              "mediaUrls": ["https://www.gol.mk/images/photo.jpg", ""]}]""",
            "https://www.gol.mk/fudbal/statija");

        assertThat(posts).hasSize(1);
        assertThat(posts.getFirst().mediaItems())
            .hasSize(1)
            .first()
            .satisfies(media -> {
                assertThat(media.type()).isEqualTo(MediaType.IMAGE);
                assertThat(media.sourceUrl()).isEqualTo("https://www.gol.mk/images/photo.jpg");
            });
    }

    @Test
    void unparseableLlmOutputYieldsNoPosts() {
        LlmClient garbageLlm = new LlmClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return "sorry, I cannot do that";
            }

            @Override
            public BotDecision decideNextAction(PageSnapshot s, String g, List<BotAction> h) {
                throw new UnsupportedOperationException();
            }
        };
        assertThat(new GolMkContentExtractor(garbageLlm)
            .extract(new PageSnapshot("u", "t", "text", null))).isEmpty();
    }

    @Test
    void blankPagesAreSkippedWithoutAnLlmCall() {
        assertThat(new GolMkContentExtractor(fakeLlm)
            .extract(new PageSnapshot("u", "t", "  ", null))).isEmpty();
    }

    @Test
    void llmTransportErrorYieldsNoPosts() {
        LlmClient failingLlm = new LlmClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new RuntimeException("Network timeout");
            }

            @Override
            public BotDecision decideNextAction(PageSnapshot s, String g, List<BotAction> h) {
                throw new UnsupportedOperationException();
            }
        };
        assertThat(new GolMkContentExtractor(failingLlm)
            .extract(new PageSnapshot("u", "t", "text", null))).isEmpty();
    }
}
