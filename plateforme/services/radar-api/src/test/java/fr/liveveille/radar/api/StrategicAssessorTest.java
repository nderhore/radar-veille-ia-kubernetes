package fr.liveveille.radar.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fr.liveveille.radar.api.config.RadarProperties.Axis;
import fr.liveveille.radar.api.config.RadarProperties.Kiq;
import fr.liveveille.radar.api.config.RadarProperties.Protocol;
import fr.liveveille.radar.api.config.RadarProperties.RingPolicy;
import fr.liveveille.radar.api.config.RadarProperties.Technology;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Observation;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Topic;
import fr.liveveille.radar.api.radar.StrategicAssessor;
import fr.liveveille.radar.api.radar.StrategicAssessor.Assessment;
import fr.liveveille.radar.api.radar.StrategicAssessor.AxisCoverage;

class StrategicAssessorTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    private static final Protocol PROTOCOL = new Protocol("2.0.0", "finalité", "CODIR",
            List.of(new Axis("KIT-2", "Agents & Applications", "Agents", "Nouvelles offres",
                    List.of(new Kiq("KIQ-2.1", "Quels processus deviennent automatisables ?", "Lancement d'une offre", "Direction de l'offre", "6 mois"),
                            new Kiq("KIQ-2.2", "Quels standards s'imposent ?", "Standard d'intégration retenu", "Architecte en chef", "12 mois")))),
            Map.of("agir", new RingPolicy("Agir", "0–6 mois", "Décider", "instruire une décision.", 14),
                   "preparer", new RingPolicy("Préparer", "6–18 mois", "Expérimenter", "lancer une preuve de concept.", 30),
                   "explorer", new RingPolicy("Explorer", "18–36 mois", "Approfondir", "rédiger une fiche.", 90),
                   "surveiller", new RingPolicy("Surveiller", "> 36 mois", "Surveiller", "réévaluer.", 180)),
            List.of(new Technology("Model Context Protocol", "KIT-2", "KIQ-2.2", "opportunite", "Un seul connecteur", List.of("mcp")),
                    new Technology("Computer use", "KIT-2", "KIQ-2.1", "mixte", "Automatiser sans API", List.of("computer use"))));

    private static Topic topic(String label, double g2, String ring) {
        return new Topic(label, "Agents & Applications", 30, 100, g2, 0.5, 0.8, 0.5, 0.2, 0.7, 0.3,
                List.of("academic", "code", "community"), ring, 5, List.of("https://ex.org"));
    }

    @Test
    void eachTopicIsLinkedToItsAxisKiqAndADatedAction() {
        List<Assessment> result = StrategicAssessor.assess(List.of(topic("Model Context Protocol", 25.0, "preparer")),
                PROTOCOL, 10.83, LocalDate.of(2026, 10, 7));
        Assessment a = result.getFirst();
        assertThat(a.axe()).isEqualTo("KIT-2");
        assertThat(a.kiq()).isEqualTo("KIQ-2.2");
        assertThat(a.proprietaire()).isEqualTo("Architecte en chef");
        assertThat(a.action()).startsWith("Expérimenter (KIQ-2.2) : lancer une preuve de concept.")
                .contains("accélère").contains("Responsable : Architecte en chef");
        assertThat(a.tendance()).startsWith("accélération significative");
        assertThat(a.revueAvant()).isEqualTo(LocalDate.of(2026, 10, 21));   // revue avancée à 14 jours
    }

    @Test
    void decliningTopicTriggersABanalisationWarning() {
        Assessment a = StrategicAssessor.assess(List.of(topic("Computer use", -15.0, "agir")), PROTOCOL, 10.83,
                LocalDate.of(2026, 10, 7)).getFirst();
        assertThat(a.tendance()).startsWith("recul relatif");
        assertThat(a.action()).contains("vérifier s'il est devenu un standard");
    }

    @Test
    void coverageFlagsBlindSpots() {
        List<Observation> obs = List.of(
                new Observation("hackernews", "community", "New MCP server", "https://a", NOW.minus(Duration.ofDays(2)), 10),
                new Observation("github", "code", "mcp toolkit", "https://b", NOW.minus(Duration.ofDays(3)), 50));
        List<AxisCoverage> cov = StrategicAssessor.coverage(obs, List.of(), PROTOCOL, NOW, 14);
        AxisCoverage kit2 = cov.getFirst();
        assertThat(kit2.signals()).isEqualTo(2);
        assertThat(kit2.couverture()).isEqualTo("partielle");                // 2 types de sources sur 3 requis
        assertThat(kit2.kiq()).extracting(StrategicAssessor.KiqCoverage::statut)
                .containsExactly("angle mort", "signaux faibles");             // KIQ-2.1 sans signal, KIQ-2.2 < 5
    }
}
