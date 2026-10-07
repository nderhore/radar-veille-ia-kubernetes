package fr.liveveille.radar.api.signal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API interne d'ingestion. Elle n'est pas exposée par la passerelle : seule l'identité
 * de service du collecteur y est autorisée (AuthorizationPolicy Istio, mTLS).
 */
@RestController
@RequestMapping("/internal/signals")
public class IngestController {

    private static final Logger log = LoggerFactory.getLogger(IngestController.class);

    private final SignalRepository repository;
    private final MeterRegistry meters;
    private final Clock clock;

    public IngestController(SignalRepository repository, MeterRegistry meters, Clock clock) {
        this.repository = repository;
        this.meters = meters;
        this.clock = clock;
    }

    @PostMapping
    @Transactional
    public Map<String, Integer> ingest(@RequestBody @NotEmpty @Size(max = 1000) List<@Valid SignalPayload> batch) {
        int inserted = 0;
        Instant now = clock.instant();
        for (SignalPayload p : batch) {
            String id = signalId(p.source(), p.url());
            var existing = repository.findById(id);
            if (existing.isPresent()) {
                existing.get().setEngagement(p.engagement());   // l'engagement évolue, le signal reste unique
                continue;
            }
            repository.save(new SignalEntity(id, p.source(), p.sourceType(), p.title(), p.summary(), p.url(),
                    p.publishedAt(), p.engagement(), now));
            meters.counter("radar.signals.ingested", "source", p.source()).increment();
            inserted++;
        }
        log.info("lot ingéré : {} reçus, {} nouveaux", batch.size(), inserted);
        return Map.of("received", batch.size(), "inserted", inserted);
    }

    /** Empreinte stable source + URL canonique : dédoublonnage intra-source (cf. radar Python). */
    static String signalId(String source, String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase().replaceFirst("^www\\.", "");
            String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath().replaceAll("/+$", "");
            String key = source + "|https://" + host + (path.isEmpty() ? "/" : path);
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
