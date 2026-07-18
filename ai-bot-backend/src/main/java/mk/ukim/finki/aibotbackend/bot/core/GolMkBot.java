package mk.ukim.finki.aibotbackend.bot.core;

import mk.ukim.finki.aibotbackend.bot.browser.BrowserAgent;
import mk.ukim.finki.aibotbackend.bot.extraction.ContentExtractor;
import mk.ukim.finki.aibotbackend.bot.extraction.LanguageDetector;
import mk.ukim.finki.aibotbackend.bot.llm.LlmClient;
import mk.ukim.finki.aibotbackend.config.BotProperties;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import org.springframework.stereotype.Component;

/**
 * Bot for gol.mk — a public Macedonian sports portal. There is no account
 * system, so "login" simply boots the browser and lands on the homepage.
 */
@Component
public class GolMkBot extends AbstractSocialNetworkBot {
    private static final String HOME = "https://www.gol.mk/";

    public GolMkBot(
        BrowserAgent browserAgent,
        LlmClient llmClient,
        ContentExtractor contentExtractor,
        LanguageDetector languageDetector,
        BotProperties botProperties
    ) {
        super(browserAgent, llmClient, contentExtractor, languageDetector, botProperties);
    }

    @Override
    public SocialNetwork network() {
        return SocialNetwork.SPORTS_PORTAL_GOL;
    }

    @Override
    public void login() {
        browserAgent.start();
        browserAgent.navigateTo(HOME);
    }

    @Override
    protected String buildGoal(ExtractionTarget target) {
        String rules = " Listing and scoreboard pages are only for finding links:"
            + " from them NAVIGATE into individual match reports and articles, and"
            + " use EXTRACT only on a page showing one full article or match report."
            + " Prefer NAVIGATE with absolute URLs shown in [brackets] in the page text."
            + " Use FINISH once you have extracted from 3 to 5 articles.";
        return switch (target.getType()) {
            case FEED_URL -> "Open %s on gol.mk, a Macedonian sports portal, and extract the individual match reports and articles linked from there.%s"
                .formatted(target.getValue(), rules);
            case HASHTAG -> "Open the '%s' section of gol.mk (start at %s and use the section navigation) and extract the most recent match reports and results.%s"
                .formatted(target.getValue(), HOME, rules);
            case KEYWORD -> "Starting from %s, find the most recent gol.mk articles about '%s' and extract them.%s"
                .formatted(HOME, target.getValue(), rules);
            case PROFILE -> "Starting from %s, find the most recent gol.mk match reports and results about the team or competition '%s' and extract them.%s"
                .formatted(HOME, target.getValue(), rules);
        };
    }
}
