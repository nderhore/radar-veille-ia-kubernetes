package fr.liveveille.radar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fr.liveveille.radar.api.config.RadarProperties.Weights;
import fr.liveveille.radar.api.radar.EmergenceCalculator;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Observation;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Settings;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Topic;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Watch;

class EmergenceCalculatorTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Settings SETTINGS = new Settings(14, 60, new Weights(0.35, 0.25, 0.20, 0.20),
            Map.of("academic", 0.8, "code", 0.6, "community", 0.6));
    private static final List<Watch> TECHS = List.of(
            new Watch("Speculative decoding", "Infrastructure & Outillage", List.of("speculative decoding")),
            new Watch("RAG", "Infrastructure & Outillage", List.of("rag", "retrieval-augmented generation")));

    private static Observation obs(String source, String type, String text, int daysAgo, double engagement) {
        return new Observation(source, type, text, "https://ex.org/" + source + "/" + text.hashCode() + "/" + daysAgo,
                T0.minus(Duration.ofDays(daysAgo)), engagement);
    }

    @Test
    void g2MatchesReferenceValueFromPythonRadar() {
        // Exemple du support de cours : on-policy distillation, a=35/3594, b=28/8537 : G² = 18,38
        assertThat(EmergenceCalculator.g2(35, 28, 3594, 8537)).isCloseTo(18.38, within(0.01));
        assertThat(EmergenceCalculator.g2(5, 80, 1000, 4000)).isNegative();
        assertThat(EmergenceCalculator.g2(10, 40, 1000, 4000)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void burstingTechnologyRanksAboveStableOne() {
        List<Observation> all = new ArrayList<>();
        for (int d = 15; d < 74; d++) {
            all.add(obs("arxiv", "academic", "a rag pipeline study " + d, d, 0));
            all.add(obs("arxiv", "academic", "unrelated topic " + d, d, 0));
        }
        for (int d = 1; d < 14; d++) {
            all.add(obs("arxiv", "academic", "rag benchmark " + d, d, 0));
            all.add(obs("github", "code", "fast speculative decoding engine " + d, d, 100 + d));
        }
        all.add(obs("github", "code", "anchor", 70, 1));

        List<Topic> topics = EmergenceCalculator.compute(all, TECHS, Set.of(), T0, SETTINGS);

        assertThat(topics).extracting(Topic::label).containsExactly("Speculative decoding", "RAG");
        Topic burst = topics.getFirst();
        assertThat(burst.novelty()).isEqualTo(1.0);
        assertThat(burst.g2()).isGreaterThan(10.83);
        assertThat(burst.ring()).isEqualTo("explorer");
    }

    @Test
    void noiseVerdictRemovesTopicAndTruncatedSourcesAreIgnored() {
        List<Observation> all = new ArrayList<>(List.of(obs("arxiv", "academic", "anchor", 70, 0)));
        for (int d = 1; d < 10; d++) {
            all.add(obs("arxiv", "academic", "speculative decoding trick " + d, d, 0));
            all.add(obs("rss:feed", "press", "rag news " + d, d, 0));     // source sans historique : exclue
        }
        List<Topic> topics = EmergenceCalculator.compute(all, TECHS, Set.of("speculative decoding"), T0, SETTINGS);
        assertThat(topics).isEmpty();
    }
}
