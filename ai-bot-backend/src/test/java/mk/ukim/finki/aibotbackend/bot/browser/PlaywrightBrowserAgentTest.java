package mk.ukim.finki.aibotbackend.bot.browser;

import mk.ukim.finki.aibotbackend.config.BotProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a real headless Chromium (downloaded automatically by Playwright on
 * first run — expect a one-time delay) against an inline data: URL.
 */
public class PlaywrightBrowserAgentTest {
    private final PlaywrightBrowserAgent agent = new PlaywrightBrowserAgent(new BotProperties(50, true));

    @AfterEach
    void tearDown() {
        agent.close();
    }

    @Test
    void snapshotContainsTextAndAnnotatedLinks() {
        agent.start();
        agent.navigateTo("data:text/html;charset=UTF-8,<html><head><title>Тест</title></head>"
            + "<body><h1>Здраво Македонијо</h1>"
            + "<a href='https://example.com/x'>Линк</a>"
            + "<script>var hidden = 'NOISE';</script></body></html>");

        PageSnapshot snapshot = agent.snapshot();

        assertThat(snapshot.title()).isEqualTo("Тест");
        assertThat(snapshot.domContent())
            .contains("Здраво Македонијо")
            .contains("https://example.com/x")
            .doesNotContain("NOISE");
        assertThat(snapshot.screenshotBase64()).isNull();
    }

    @Test
    void screenshotReturnsPngBytes() {
        agent.start();
        agent.navigateTo("data:text/html;charset=UTF-8,<html><body><p>x</p></body></html>");
        assertThat(agent.takeScreenshot()).isNotEmpty();
    }
}
