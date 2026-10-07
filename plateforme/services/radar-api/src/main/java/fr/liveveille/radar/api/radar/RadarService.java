package fr.liveveille.radar.api.radar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.liveveille.radar.api.config.RadarProperties;
import fr.liveveille.radar.api.feedback.FeedbackRepository;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Observation;
import fr.liveveille.radar.api.radar.EmergenceCalculator.Topic;
import fr.liveveille.radar.api.radar.StrategicAssessor.Assessment;
import fr.liveveille.radar.api.radar.StrategicAssessor.AxisCoverage;
import fr.liveveille.radar.api.signal.SignalRepository;

/**
 * Recalcule périodiquement le radar, sa lecture stratégique (axes, KIQ, actions) et la couverture
 * du protocole, puis les expose sous forme de métriques Prometheus. Les métriques métier alimentent
 * le tableau de bord du protocole et l'alerte destinée à l'équipe veille.
 */
@Service
public class RadarService {

    private static final Logger log = LoggerFactory.getLogger(RadarService.class);

    public record Snapshot(Instant computedAt, Instant asOf, String protocolVersion, int signalsInWindow,
                           List<Assessment> topics, List<AxisCoverage> coverage) {
    }

    private final SignalRepository signals;
    private final FeedbackRepository feedback;
    private final RadarProperties props;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();
    private final MultiGauge disruptionGauge;
    private final MultiGauge axisSignalsGauge;
    private final MultiGauge axisSourceTypesGauge;
    private final MultiGauge kiqSignalsGauge;
    private final MultiGauge ringGauge;
    private final AtomicInteger emergingGauge;
    private final Timer computeTimer;

    public RadarService(SignalRepository signals, FeedbackRepository feedback, RadarProperties props, Clock clock,
                        MeterRegistry registry) {
        this.signals = signals;
        this.feedback = feedback;
        this.props = props;
        this.clock = clock;
        this.disruptionGauge = MultiGauge.builder("radar.topic.disruption.index")
                .description("Indice de rupture par sujet, avec son axe, sa KIQ, son anneau et sa nature").register(registry);
        this.axisSignalsGauge = MultiGauge.builder("radar.axis.signals")
                .description("Signaux récents rattachés à chaque axe du protocole").register(registry);
        this.axisSourceTypesGauge = MultiGauge.builder("radar.axis.source.types")
                .description("Nombre de types de sources distincts couvrant chaque axe").register(registry);
        this.kiqSignalsGauge = MultiGauge.builder("radar.kiq.signals")
                .description("Signaux récents disponibles pour répondre à chaque KIQ").register(registry);
        this.ringGauge = MultiGauge.builder("radar.topics.ring")
                .description("Nombre de sujets par axe et par anneau").register(registry);
        this.emergingGauge = registry.gauge("radar.topics.emerging", new AtomicInteger());
        this.computeTimer = Timer.builder("radar.compute.duration").description("Durée du calcul du radar")
                .register(registry);
    }

    public Snapshot current() {
        Snapshot s = snapshot.get();
        return s != null ? s : recompute();
    }

    public RadarProperties.Protocol protocol() {
        return props.protocole();
    }

    @Scheduled(fixedDelayString = "${radar.recompute-interval:PT5M}", initialDelayString = "PT15S")
    @Transactional(readOnly = true)
    public Snapshot recompute() {
        return computeTimer.record(() -> {
            Instant asOf = clock.instant();
            Instant start = asOf.minus(Duration.ofDays(props.recentDays() + props.baselineDays()));
            List<Observation> observations = signals.findByPublishedAtAfter(start).stream()
                    .map(s -> new Observation(s.getSource(), s.getSourceType(),
                            s.getTitle() + " " + (s.getSummary() == null ? "" : s.getSummary()),
                            s.getUrl(), s.getPublishedAt(), s.getEngagement()))
                    .toList();
            Set<String> noise = feedback.findAll().stream().filter(f -> "bruit".equals(f.getVerdict()))
                    .map(f -> f.getTerm().toLowerCase()).collect(Collectors.toSet());

            RadarProperties.Protocol protocol = props.protocole();
            List<Topic> topics = EmergenceCalculator.compute(observations, StrategicAssessor.watches(protocol), noise, asOf,
                    new EmergenceCalculator.Settings(props.recentDays(), props.baselineDays(), props.weights(),
                            props.credibility()));
            List<Assessment> assessments = StrategicAssessor.assess(topics, protocol, props.g2Threshold(),
                    StrategicAssessor.todayUtc(asOf));
            List<AxisCoverage> coverage = StrategicAssessor.coverage(observations, assessments, protocol, asOf,
                    props.recentDays());

            publishMetrics(assessments, coverage);
            Snapshot s = new Snapshot(clock.instant(), asOf, protocol.version(), observations.size(), assessments, coverage);
            snapshot.set(s);
            log.info("radar recalculé : {} signaux dans la fenêtre, {} sujets, couverture {}", observations.size(),
                    assessments.size(), coverage.stream().map(c -> c.id() + "=" + c.couverture()).toList());
            return s;
        });
    }

    private void publishMetrics(List<Assessment> assessments, List<AxisCoverage> coverage) {
        disruptionGauge.register(assessments.stream()
                .map(a -> MultiGauge.Row.of(Tags.of("topic", a.label(), "kit", a.axe(), "kiq", a.kiq(),
                        "quadrant", a.quadrant(), "ring", a.ring(), "nature", a.nature()), a.disruptionIndex()))
                .toList(), true);
        axisSignalsGauge.register(coverage.stream()
                .map(c -> MultiGauge.Row.of(Tags.of("kit", c.id(), "couverture", c.couverture()), c.signals()))
                .toList(), true);
        axisSourceTypesGauge.register(coverage.stream()
                .map(c -> MultiGauge.Row.of(Tags.of("kit", c.id()), c.sourceTypes().size()))
                .toList(), true);
        kiqSignalsGauge.register(coverage.stream()
                .flatMap(c -> c.kiq().stream().map(k -> MultiGauge.Row.of(
                        Tags.of("kit", c.id(), "kiq", k.id(), "statut", k.statut()), k.signals())))
                .toList(), true);
        ringGauge.register(coverage.stream()
                .flatMap(c -> c.sujetsParAnneau().entrySet().stream().map(e -> MultiGauge.Row.of(
                        Tags.of("kit", c.id(), "ring", e.getKey()), e.getValue())))
                .toList(), true);
        emergingGauge.set((int) assessments.stream().filter(a -> a.g2() >= props.g2Threshold()).count());
    }
}
