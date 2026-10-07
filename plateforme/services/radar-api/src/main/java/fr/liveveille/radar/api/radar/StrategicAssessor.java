package fr.liveveille.radar.api.radar;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import fr.liveveille.radar.api.config.RadarProperties.Axis;
import fr.liveveille.radar.api.config.RadarProperties.Kiq;
import fr.liveveille.radar.api.config.RadarProperties.Protocol;
import fr.liveveille.radar.api.config.RadarProperties.RingPolicy;
import fr.liveveille.radar.api.config.RadarProperties.Technology;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Observation;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Topic;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Watch;

/**
 * Lecture stratégique du radar : rattache chaque sujet à son axe (KIT) et à sa question décisionnelle
 * (KIQ), qualifie sa tendance, formule l'action recommandée et mesure la couverture du protocole.
 *
 * <p>C'est la traduction exécutable du protocole de veille : un sujet n'est jamais présenté seul,
 * mais toujours comme un élément de réponse à une question posée par un décideur.
 */
public final class StrategicAssessor {

    /** Sujet du radar lu à travers le protocole. */
    public record Assessment(
            String label, String axe, String axeIntitule, String quadrant,
            String kiq, String question, String decision, String proprietaire, String horizonDecision,
            String nature, String opportunite, String tendance,
            String ring, String ringLabel, String ringHorizon, int trl,
            String action, LocalDate revueAvant,
            int recent, int baseline, double g2, double diffusion, double impact, double novelty,
            double disruptionIndex, List<String> sourceTypes, List<String> evidenceUrls) {
    }

    /** Couverture d'une KIQ : dispose-t-on de signaux récents pour y répondre ? */
    public record KiqCoverage(String id, String question, String decision, String proprietaire, String horizon,
                              int signals, int technologies, String statut) {
    }

    /** Couverture d'un axe : volume, diversité des sources, sujets par anneau, KIQ renseignées. */
    public record AxisCoverage(String id, String intitule, String quadrant, String enjeu,
                               int technologies, int signals, List<String> sourceTypes, String couverture,
                               Map<String, Integer> sujetsParAnneau, String sujetPrincipal, List<KiqCoverage> kiq) {
    }

    private StrategicAssessor() {
    }

    /** Technologies du protocole sous la forme attendue par le calcul d'émergence. */
    public static List<Watch> watches(Protocol protocol) {
        return protocol.technologies().stream()
                .map(t -> new Watch(t.label(), protocol.axis(t.axe()).map(Axis::quadrant).orElse(t.axe()), t.synonyms()))
                .toList();
    }

    public static List<Assessment> assess(List<Topic> topics, Protocol protocol, double g2Threshold, LocalDate today) {
        Map<String, Technology> byLabel = new LinkedHashMap<>();
        protocol.technologies().forEach(t -> byLabel.put(t.label(), t));
        List<String> order = List.of("agir", "preparer", "explorer", "surveiller");
        List<Assessment> result = new ArrayList<>();
        for (Topic t : topics) {
            Technology tech = byLabel.get(t.label());
            if (tech == null) {
                continue;
            }
            Axis axis = protocol.axis(tech.axe()).orElseThrow();
            Kiq kiq = axis.kiq(tech.kiq()).orElseThrow();
            RingPolicy policy = protocol.anneaux().get(t.ring());
            Trend trend = trend(t.g2(), g2Threshold);
            int reviewDays = trend == Trend.ACCELERATION ? Math.min(policy.revueJours(), 14) : policy.revueJours();
            String action = policy.verbe() + " (" + kiq.id() + ") : " + policy.consigne()
                    + trend.advice
                    + " Décision éclairée : " + lowerFirst(kiq.decision()) + ". Responsable : " + kiq.proprietaire() + ".";
            result.add(new Assessment(t.label(), axis.id(), axis.intitule(), axis.quadrant(),
                    kiq.id(), kiq.question(), kiq.decision(), kiq.proprietaire(), kiq.horizon(),
                    tech.nature(), tech.opportunite(), trend.label(t.g2()),
                    t.ring(), policy.libelle(), policy.horizon(), t.trl(),
                    action, today.plusDays(reviewDays),
                    t.recent(), t.baseline(), t.g2(), t.diffusion(), t.impact(), t.novelty(),
                    t.disruptionIndex(), t.sourceTypes(), t.evidenceUrls()));
        }
        result.sort(Comparator.comparingInt((Assessment a) -> order.indexOf(a.ring()))
                .thenComparing(Comparator.comparingDouble(Assessment::disruptionIndex).reversed()));
        return result;
    }

    /**
     * Couverture stratégique : pour chaque axe, nombre de signaux récents rattachés et diversité des
     * types de sources ; pour chaque KIQ, existence de signaux permettant d'y répondre. Une KIQ sans
     * signal est un angle mort du dispositif : il faut revoir le plan de sourcing.
     */
    public static List<AxisCoverage> coverage(List<Observation> observations, List<Assessment> assessments,
                                              Protocol protocol, Instant asOf, int recentDays) {
        Instant start = asOf.minus(Duration.ofDays(recentDays));
        List<Observation> recent = observations.stream().filter(o -> o.publishedAt().isAfter(start)).toList();
        List<AxisCoverage> result = new ArrayList<>();
        for (Axis axis : protocol.axes()) {
            List<Technology> techs = protocol.technologies().stream().filter(t -> t.axe().equals(axis.id())).toList();
            Set<Observation> axisHits = new java.util.HashSet<>();
            Set<String> types = new TreeSet<>();
            List<KiqCoverage> kiqs = new ArrayList<>();
            for (Kiq kiq : axis.kiq()) {
                List<Technology> kiqTechs = techs.stream().filter(t -> t.kiq().equals(kiq.id())).toList();
                Set<Observation> kiqHits = new java.util.HashSet<>();
                for (Technology tech : kiqTechs) {
                    Pattern p = new Watch(tech.label(), axis.quadrant(), tech.synonyms()).pattern();
                    recent.stream().filter(o -> p.matcher(o.text()).find()).forEach(kiqHits::add);
                }
                kiqHits.forEach(o -> types.add(o.sourceType()));
                axisHits.addAll(kiqHits);
                String statut = kiqTechs.isEmpty() ? "aucune technologie suivie"
                        : kiqHits.isEmpty() ? "angle mort" : kiqHits.size() < 5 ? "signaux faibles" : "renseignée";
                kiqs.add(new KiqCoverage(kiq.id(), kiq.question(), kiq.decision(), kiq.proprietaire(), kiq.horizon(),
                        kiqHits.size(), kiqTechs.size(), statut));
            }
            Map<String, Integer> byRing = new LinkedHashMap<>();
            for (String ring : List.of("agir", "preparer", "explorer", "surveiller")) {
                byRing.put(ring, (int) assessments.stream().filter(a -> a.axe().equals(axis.id()) && a.ring().equals(ring)).count());
            }
            String principal = assessments.stream().filter(a -> a.axe().equals(axis.id()))
                    .max(Comparator.comparingDouble(Assessment::disruptionIndex)).map(Assessment::label).orElse("-");
            String couverture = axisHits.isEmpty() ? "insuffisante" : types.size() >= 3 ? "complète" : "partielle";
            result.add(new AxisCoverage(axis.id(), axis.intitule(), axis.quadrant(), axis.enjeu(), techs.size(),
                    axisHits.size(), List.copyOf(types), couverture, byRing, principal, kiqs));
        }
        return result;
    }

    enum Trend {
        ACCELERATION(" Le sujet accélère : revue avancée."),
        PROGRESSION(""),
        STABLE(""),
        RECUL(" Le sujet recule dans les sources : vérifier s'il est devenu un standard avant d'investir.");

        final String advice;

        Trend(String advice) {
            this.advice = advice;
        }

        String label(double g2) {
            String v = String.format(java.util.Locale.FRANCE, "%.1f", g2);
            return switch (this) {
                case ACCELERATION -> "accélération significative (G² = " + v + ")";
                case PROGRESSION -> "progression (G² = " + v + ")";
                case STABLE -> "stable (G² = " + v + ")";
                case RECUL -> "recul relatif (G² = " + v + ")";
            };
        }
    }

    static Trend trend(double g2, double threshold) {
        if (g2 >= threshold) {
            return Trend.ACCELERATION;
        }
        if (g2 <= -threshold) {
            return Trend.RECUL;
        }
        return g2 > 1 ? Trend.PROGRESSION : Trend.STABLE;
    }

    static LocalDate todayUtc(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
