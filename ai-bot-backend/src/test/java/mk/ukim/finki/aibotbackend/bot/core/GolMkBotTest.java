package mk.ukim.finki.aibotbackend.bot.core;

import mk.ukim.finki.aibotbackend.config.BotProperties;
import mk.ukim.finki.aibotbackend.model.domain.ExtractionTarget;
import mk.ukim.finki.aibotbackend.model.enums.SocialNetwork;
import mk.ukim.finki.aibotbackend.model.enums.TargetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GolMkBotTest {
    private final GolMkBot bot = new GolMkBot(null, null, null, null, new BotProperties(50, true));

    @Test
    void networkIsTheSportsPortal() {
        assertThat(bot.network()).isEqualTo(SocialNetwork.SPORTS_PORTAL_GOL);
    }

    @Test
    void feedUrlGoalContainsTheUrl() {
        ExtractionTarget target = new ExtractionTarget(TargetType.FEED_URL, "https://www.gol.mk/rezultati", null);
        assertThat(bot.buildGoal(target))
            .contains("https://www.gol.mk/rezultati")
            .contains("EXTRACT")
            .contains("FINISH");
    }

    @Test
    void keywordGoalContainsTheKeywordAndStartUrl() {
        ExtractionTarget target = new ExtractionTarget(TargetType.KEYWORD, "Вардар", null);
        assertThat(bot.buildGoal(target)).contains("Вардар").contains("https://www.gol.mk/");
    }
}
