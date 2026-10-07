package fr.liveveille.radar.collector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import fr.liveveille.radar.collector.config.CollectorProperties;
import fr.liveveille.radar.collector.source.HttpClients;
import fr.liveveille.radar.collector.source.SignalPayload;
import fr.liveveille.radar.collector.source.SourceClient;

/**
 * Cycle de collecte. Chaque source est isolée : une panne d'arXiv n'empêche pas la collecte
 * de Hacker News. La fraîcheur par source est exposée en métrique (alerte RadarCollectorStale).
 */
@Component
public class CollectionJob {

    private static final Logger log = LoggerFactory.getLogger(CollectionJob.class);
    private static final int CHUNK = 500;

    private final List<SourceClient> sources;
    private final CollectorProperties props;
    private final MeterRegistry meters;
    private final Clock clock;
    private final RestClient api;
    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public CollectionJob(List<SourceClient> sources, CollectorProperties props, MeterRegistry meters,
                         RestClient.Builder builder) {
        this.sources = sources;
        this.props = props;
        this.meters = meters;
        this.clock = Clock.systemUTC();
        // Appel interne au maillage : délais de sécurité côté client, mais les retries sont portés par Istio (VirtualService)
        this.api = HttpClients.build(builder, props.apiUrl());
        sources.forEach(s -> meters.gauge("radar.collector.last.success", List.of(Tag.of("source", s.name())),
                lastSuccess.computeIfAbsent(s.name(), k -> new AtomicLong(0)), AtomicLong::doubleValue));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (props.runOnStartup()) {
            Thread.ofVirtual().name("collecte-initiale").start(this::collectAll);
        }
    }

    @Scheduled(cron = "${collector.cron}")
    public void collectAll() {
        Instant since = clock.instant().minus(Duration.ofDays(props.lookbackDays()));
        for (SourceClient source : sources) {
            if (!source.enabled()) {
                continue;
            }
            Timer.Sample sample = Timer.start(meters);
            try {
                List<SignalPayload> batch = source.fetch(since);
                for (int i = 0; i < batch.size(); i += CHUNK) {
                    api.post().uri("/internal/signals").body(batch.subList(i, Math.min(i + CHUNK, batch.size())))
                            .retrieve().toBodilessEntity();
                }
                meters.counter("radar.collector.signals", "source", source.name()).increment(batch.size());
                lastSuccess.get(source.name()).set(clock.instant().getEpochSecond());
                log.info("source {} : {} signaux transmis", source.name(), batch.size());
            } catch (RuntimeException e) {
                meters.counter("radar.collector.errors", "source", source.name()).increment();
                log.error("source {} en échec : {}", source.name(), e.getMessage());
            } finally {
                sample.stop(meters.timer("radar.collector.duration", "source", source.name()));
            }
        }
    }
}
