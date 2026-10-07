package fr.liveveille.radar.api.radar;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import fr.liveveille.radar.api.config.RadarProperties.Weights;

/**
 * Détection d'émergence sur la liste de surveillance : portage Java de detect.py (démo 1).
 *
 * <p>Pour chaque technologie : comparaison de sa fréquence documentaire entre une période récente R
 * et une période de référence B par le rapport de vraisemblance G² de Dunning (1993), puis indice
 * de rupture IR = Fiabilité × (w_m·Momentum + w_d·Diffusion + w_i·Impact + w_n·Nouveauté).
 * Classe sans état ni dépendance Spring : entièrement testable unitairement.
 */
public final class EmergenceCalculator {

    public static final int SOURCE_TYPE_COUNT = 5;

    public record Observation(String source, String sourceType, String text, String url, Instant publishedAt,
                              double engagement) {
    }

    /** Technologie à suivre : libellé, quadrant et synonymes (le rattachement stratégique est fait ailleurs). */
    public record Watch(String label, String quadrant, List<String> synonyms) {

        public Pattern pattern() {
            return Pattern.compile("\\b(" + synonyms.stream().map(Pattern::quote).collect(Collectors.joining("|")) + ")\\b",
                    Pattern.CASE_INSENSITIVE);
        }
    }

    public record Topic(String label, String quadrant, int recent, int baseline, double g2, double momentum,
                        double diffusion, double impact, double novelty, double credibility,
                        double disruptionIndex, List<String> sourceTypes, String ring, int trl,
                        List<String> evidenceUrls) {
    }

    public record Settings(int recentDays, int baselineDays, Weights weights, Map<String, Double> credibility) {
    }

    private EmergenceCalculator() {
    }

    /** G² signé (table 2×2) : positif si le terme est sur-représenté en période récente. */
    public static double g2(double a, double b, double nRecent, double nBaseline) {
        double c = nRecent - a, d = nBaseline - b, total = nRecent + nBaseline;
        double[] observed = {a, b, c, d};
        double[] expected = {nRecent * (a + b) / total, nBaseline * (a + b) / total,
                nRecent * (c + d) / total, nBaseline * (c + d) / total};
        double stat = 0;
        for (int i = 0; i < 4; i++) {
            if (observed[i] > 0 && expected[i] > 0) {
                stat += observed[i] * Math.log(observed[i] / expected[i]);
            }
        }
        stat *= 2;
        double baselineRate = nBaseline > 0 ? b / nBaseline : 0;
        return (a / nRecent) >= baselineRate ? stat : -stat;
    }

    public static List<Topic> compute(List<Observation> all, List<Watch> technologies, Set<String> noise,
                                      Instant asOf, Settings settings) {
        Instant tR = asOf.minus(Duration.ofDays(settings.recentDays()));
        Instant tB = tR.minus(Duration.ofDays(settings.baselineDays()));
        List<Observation> observations = coverageFilter(all, tB, tR);

        List<Observation> recent = observations.stream()
                .filter(o -> o.publishedAt().isAfter(tR) && !o.publishedAt().isAfter(asOf)).toList();
        List<Observation> baseline = observations.stream()
                .filter(o -> o.publishedAt().isAfter(tB) && !o.publishedAt().isAfter(tR)).toList();
        if (recent.isEmpty() || baseline.isEmpty()) {
            return List.of();
        }
        Map<Observation, Double> percentiles = engagementPercentiles(recent);

        record Raw(Watch tech, int a, int b, double g2, double diffusion, double impact, double novelty,
                   double credibility, List<String> types, List<String> urls) {
        }
        List<Raw> raws = new ArrayList<>();
        for (Watch tech : technologies) {
            if (noise.contains(tech.label().toLowerCase())) {
                continue;   // jugée « bruit » par un analyste
            }
            Pattern pattern = tech.pattern();
            List<Observation> hits = recent.stream().filter(o -> pattern.matcher(o.text()).find()).toList();
            if (hits.isEmpty()) {
                continue;
            }
            int a = hits.size();
            int b = (int) baseline.stream().filter(o -> pattern.matcher(o.text()).find()).count();
            List<String> types = new ArrayList<>(new TreeSet<>(hits.stream().map(Observation::sourceType).toList()));
            double impact = hits.stream().map(percentiles::get).filter(v -> v != null)
                    .sorted(Comparator.reverseOrder()).limit(5).mapToDouble(Double::doubleValue).average().orElse(0);
            double credibility = hits.stream()
                    .mapToDouble(o -> settings.credibility().getOrDefault(o.sourceType(), 0.5)).average().orElse(0.5);
            List<String> urls = hits.stream().sorted(Comparator.comparingDouble(Observation::engagement).reversed())
                    .limit(5).map(Observation::url).toList();
            raws.add(new Raw(tech, a, b, g2(a, b, recent.size(), baseline.size()), (double) types.size() / SOURCE_TYPE_COUNT,
                    impact, (double) a / (a + b), credibility, types, urls));
        }

        double g2Max = Math.max(1.0, raws.stream().mapToDouble(r -> Math.max(r.g2(), 0)).max().orElse(1.0));
        Weights w = settings.weights();
        List<Topic> topics = new ArrayList<>();
        for (Raw r : raws) {
            double momentum = Math.log1p(Math.max(r.g2(), 0)) / Math.log1p(g2Max);
            double score = w.momentum() * momentum + w.diffusion() * r.diffusion() + w.impact() * r.impact()
                    + w.novelty() * r.novelty();
            Maturity m = maturity(r.types(), r.a() + r.b());
            topics.add(new Topic(r.tech().label(), r.tech().quadrant(), r.a(), r.b(), round(r.g2(), 2), round(momentum, 3),
                    round(r.diffusion(), 3), round(r.impact(), 3), round(r.novelty(), 3), round(r.credibility(), 3),
                    round(r.credibility() * score, 4), r.types(), m.ring(), m.trl(), r.urls()));
        }
        topics.sort(Comparator.comparingDouble(Topic::disruptionIndex).reversed());
        return topics;
    }

    record Maturity(String ring, int trl) {
    }

    /**
     * Maturité inférée de la typologie des sources (modèle de diffusion : académique, code :
     * communauté, presse, réglementation). Heuristique : la qualification fine reste humaine.
     */
    static Maturity maturity(List<String> types, int cumulativeVolume) {
        Set<String> t = new HashSet<>(types);
        boolean established = cumulativeVolume >= 150;
        if ((t.contains("press") || t.contains("regulatory") || established) && t.contains("code")) {
            return new Maturity("agir", 7);
        }
        if (t.contains("code") && t.size() >= 2) {
            return new Maturity("preparer", 5);
        }
        if (t.contains("code") || t.contains("community")) {
            return new Maturity("explorer", 4);
        }
        return new Maturity("surveiller", 3);
    }

    /** Écarte les sources dont l'historique ne couvre pas la moitié de la période de référence (biais de troncature). */
    static List<Observation> coverageFilter(List<Observation> observations, Instant tB, Instant tR) {
        Instant threshold = tR.minus(Duration.between(tB, tR).dividedBy(2));
        Map<String, Instant> firstSeen = new HashMap<>();
        for (Observation o : observations) {
            firstSeen.merge(o.source(), o.publishedAt(), (x, y) -> x.isBefore(y) ? x : y);
        }
        return observations.stream().filter(o -> !firstSeen.get(o.source()).isAfter(threshold)).toList();
    }

    /** Rang centile de l'engagement au sein de chaque source (étoiles et points ne sont pas comparables). */
    static Map<Observation, Double> engagementPercentiles(List<Observation> recent) {
        Map<String, List<Observation>> bySource = recent.stream().filter(o -> o.engagement() > 0)
                .collect(Collectors.groupingBy(Observation::source));
        Map<Observation, Double> pct = new HashMap<>();
        bySource.values().forEach(group -> {
            List<Observation> sorted = group.stream().sorted(Comparator.comparingDouble(Observation::engagement)).toList();
            for (int i = 0; i < sorted.size(); i++) {
                pct.put(sorted.get(i), (i + 1.0) / sorted.size());
            }
        });
        return pct;
    }

    private static double round(double value, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(value * f) / f;
    }
}
