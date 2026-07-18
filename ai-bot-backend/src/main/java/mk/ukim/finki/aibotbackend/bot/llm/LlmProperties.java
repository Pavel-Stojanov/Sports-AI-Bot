package mk.ukim.finki.aibotbackend.bot.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the LLM provider, bound from the {@code llm.*} properties.
 * Any OpenAI-compatible chat-completions endpoint works (Groq, Gemini's
 * compatibility endpoint, OpenRouter, a local Ollama, ...).
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    String baseUrl,
    String model,
    String apiKey
) {
}
