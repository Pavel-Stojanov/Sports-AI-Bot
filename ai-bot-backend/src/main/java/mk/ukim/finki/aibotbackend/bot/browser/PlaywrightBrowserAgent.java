package mk.ukim.finki.aibotbackend.bot.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import mk.ukim.finki.aibotbackend.config.BotProperties;
import mk.ukim.finki.aibotbackend.model.exception.BotExecutionException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Playwright-backed browser agent. The snapshot is a flattened, link-annotated
 * text rendering of the page ("link text [absolute-url]"), sized for an LLM
 * prompt; no screenshots are embedded (free-tier models are text-only).
 */
@Component
public class PlaywrightBrowserAgent implements BrowserAgent {
    // Kept small because free-tier LLM providers (e.g. Groq) enforce tight
    // tokens-per-minute/tokens-per-day budgets, and every decision/extraction
    // call ships one snapshot; 15k chars (~7k tokens) burned a full day's
    // quota in a single session.
    private static final int MAX_DOM_CHARS = 8_000;

    private final BotProperties botProperties;

    private Playwright playwright;
    private Browser browser;
    private Page page;

    public PlaywrightBrowserAgent(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    @Override
    public void start() {
        close();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(botProperties.headless()));
        page = browser.newPage();
    }

    @Override
    public void navigateTo(String url) {
        requirePage().navigate(url);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    @Override
    public void click(String elementDescription) {
        resolve(elementDescription).click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    @Override
    public void type(String elementDescription, String text) {
        resolve(elementDescription).fill(text);
    }

    @Override
    public void scrollDown() {
        requirePage().evaluate("window.scrollBy(0, window.innerHeight)");
    }

    @Override
    public byte[] takeScreenshot() {
        return requirePage().screenshot();
    }

    @Override
    public PageSnapshot snapshot() {
        Page current = requirePage();
        Document document = Jsoup.parse(current.content(), current.url());
        document.select("script, style, noscript, svg, iframe, form").remove();
        for (Element link : document.select("a[href]")) {
            String href = link.absUrl("href");
            if (!href.isBlank()) {
                link.appendText(" [" + href + "]");
            }
        }
        String text = document.body() != null ? document.body().text() : document.text();
        if (text.length() > MAX_DOM_CHARS) {
            text = text.substring(0, MAX_DOM_CHARS);
        }
        return new PageSnapshot(current.url(), current.title(), text, null);
    }

    @Override
    public void close() {
        if (page != null) {
            page.close();
            page = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    /**
     * The loop hands over free-form element descriptions: try them as a CSS
     * selector first, fall back to a visible-text match.
     */
    private Locator resolve(String elementDescription) {
        try {
            Locator byCss = requirePage().locator(elementDescription);
            if (byCss.count() > 0) {
                return byCss.first();
            }
        } catch (PlaywrightException ignored) {
            // not a valid selector — fall through to text lookup
        }
        return requirePage()
            .getByText(elementDescription, new Page.GetByTextOptions().setExact(false))
            .first();
    }

    private Page requirePage() {
        if (page == null) {
            throw new BotExecutionException("BrowserAgent.start() has not been called.");
        }
        return page;
    }
}
