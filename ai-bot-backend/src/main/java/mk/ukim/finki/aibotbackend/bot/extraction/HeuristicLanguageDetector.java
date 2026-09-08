package mk.ukim.finki.aibotbackend.bot.extraction;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Scores script and common words. This is a heuristic, not a calibrated probability.
 *
 * <p>Foreign evidence is counted per word, so one Russian or Bulgarian name inside a
 * Macedonian article costs a little, while a text written in those languages loses
 * most of its score. Word evidence only counts in full once at least half of the
 * letters are Cyrillic, so a Latin-script page with a few Macedonian words stays low.</p>
 */
@Component
public class HeuristicLanguageDetector implements LanguageDetector {
    private static final String MACEDONIAN_ONLY = "ѓќѕЃЌЅ";
    /** Letters that Macedonian does not use but its Cyrillic neighbours do. */
    private static final String FOREIGN_LETTERS = "ђћъщыэёїієйяюьЂЋЪЩЫЭЁЇІЄЙЯЮЬ";
    private static final Set<String> MACEDONIAN_WORDS = Set.of(
        "ќе", "со", "во", "од", "на", "за", "се", "и", "го", "ги", "ова", "овој", "кој", "која", "дека");
    private static final Set<String> FOREIGN_WORDS = Set.of(
        "је", "у", "су", "овај", "који", "ще", "във", "това", "който", "это", "что", "как");
    private static final double FOREIGN_WORD_PENALTY = 0.15;
    private static final double MAX_FOREIGN_PENALTY = 0.6;

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
        double ratio = (double) cyrillic / letters;
        double wordWeight = Math.min(1.0, 2 * ratio);

        Set<String> words = new HashSet<>(List.of(
            text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")));
        long evidence = words.stream().filter(MACEDONIAN_WORDS::contains).count();
        long foreign = words.stream()
            .filter(word -> FOREIGN_WORDS.contains(word) || word.chars().anyMatch(c -> FOREIGN_LETTERS.indexOf(c) >= 0))
            .count();

        double score = 0.45 * ratio + wordWeight * Math.min(0.4, evidence * 0.1);
        if (text.chars().anyMatch(c -> MACEDONIAN_ONLY.indexOf(c) >= 0)) {
            score += wordWeight * 0.2;
        }
        score -= Math.min(MAX_FOREIGN_PENALTY, foreign * FOREIGN_WORD_PENALTY);
        return Math.clamp(score, 0.0, 1.0);
    }
}
