package mk.ukim.finki.aibotbackend.bot.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HeuristicLanguageDetectorTest {
    private final HeuristicLanguageDetector detector = new HeuristicLanguageDetector();

    @Test
    void macedonianWithoutDistinctiveLettersStillHasLanguageEvidence() {
        assertThat(detector.macedonianConfidence(
            "Вардар победи со два гола во натпреварот и се пласира на првото место."))
            .isGreaterThanOrEqualTo(0.8);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "Российская команда выиграла матч и вышла в финал чемпионата.",
        "Отборът спечели мача и ще играе във финала на първенството.",
        "Тим је победио у финалу и освојио титулу.",
        "Спорт футбол тенис баскетбол"
    })
    void cyrillicAloneIsNotEnoughForDonation(String text) {
        assertThat(detector.macedonianConfidence(text)).isLessThan(0.6);
    }

    @Test
    void macedonianTextScoresHigh() {
        double score = detector.macedonianConfidence(
            "Македонија победи со два гола и ќе игра во финалето на квалификациите.");
        assertThat(score).isGreaterThan(0.8);
    }

    @Test
    void englishTextScoresLow() {
        assertThat(detector.macedonianConfidence("Manchester United won the match 3-1."))
            .isLessThan(0.1);
    }

    @Test
    void serbianSpecificLettersArePenalized() {
        double score = detector.macedonianConfidence(
            "Фудбалери Ђоковића ће играти у финалу такмичења.");
        assertThat(score).isLessThan(0.4);
    }

    @Test
    void nullAndBlankScoreZero() {
        assertThat(detector.macedonianConfidence(null)).isEqualTo(0.0);
        assertThat(detector.macedonianConfidence("   ")).isEqualTo(0.0);
        assertThat(detector.macedonianConfidence("3:1 !!!")).isEqualTo(0.0);
    }
}
