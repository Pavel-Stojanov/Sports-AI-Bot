package mk.ukim.finki.aibotbackend.bot.extraction;

import org.springframework.stereotype.Component;

/**
 * Script-based Macedonian detector: Cyrillic ratio as the base signal,
 * boosted by letters unique to Macedonian (ѓ, ќ, ѕ) and heavily penalized
 * by letters unique to Serbian (ђ, ћ). Deterministic — no network calls.
 */
@Component
public class HeuristicLanguageDetector implements LanguageDetector {
    private static final String MACEDONIAN_ONLY = "ѓќѕЃЌЅ";
    private static final String SERBIAN_ONLY = "ђћЂЋ";

    @Override
    public double macedonianConfidence(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters == 0) {
            return 0.0;
        }
        long cyrillic = text.chars()
            .filter(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC)
            .count();

        double score = 0.7 * cyrillic / letters;
        if (text.chars().anyMatch(c -> MACEDONIAN_ONLY.indexOf(c) >= 0)) {
            score += 0.3;
        }
        if (text.chars().anyMatch(c -> SERBIAN_ONLY.indexOf(c) >= 0)) {
            score *= 0.2;
        }
        return Math.clamp(score, 0.0, 1.0);
    }
}
