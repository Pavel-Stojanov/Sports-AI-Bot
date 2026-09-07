package mk.ukim.finki.aibotbackend.bot.extraction;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Scores script and common words. This is a heuristic, not a calibrated probability. */
@Component
public class HeuristicLanguageDetector implements LanguageDetector {
    private static final String MACEDONIAN_ONLY = "ѓќѕЃЌЅ";
    private static final String FOREIGN_LETTERS = "ђћъщыэёїієЂЋЪЩЫЭЁЇІЄ";
    private static final Set<String> MACEDONIAN_WORDS = Set.of(
        "ќе", "со", "во", "од", "на", "за", "се", "и", "го", "ги", "ова", "овој", "кој", "која", "дека");
    private static final Set<String> FOREIGN_WORDS = Set.of(
        "је", "у", "су", "овај", "који", "ще", "във", "това", "който", "это", "что", "как");

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
        double score = 0.45 * ratio;
        Set<String> words = new HashSet<>(List.of(
            text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")));
        long evidence = words.stream().filter(MACEDONIAN_WORDS::contains).count();
        score += Math.min(0.4, evidence * 0.1) * ratio;
        if (text.chars().anyMatch(c -> MACEDONIAN_ONLY.indexOf(c) >= 0)) {
            score += 0.2 * ratio;
        }
        if (text.chars().anyMatch(c -> FOREIGN_LETTERS.indexOf(c) >= 0)
            || words.stream().anyMatch(FOREIGN_WORDS::contains)) {
            score *= 0.2;
        }
        return Math.clamp(score, 0.0, 1.0);
    }
}
