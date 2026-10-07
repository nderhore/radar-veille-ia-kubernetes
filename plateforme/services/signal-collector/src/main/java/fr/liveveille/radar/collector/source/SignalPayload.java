package fr.liveveille.radar.collector.source;

import java.time.Instant;

/** Contrat d'échange avec radar-api (POST /internal/signals). */
public record SignalPayload(String source, String sourceType, String title, String summary, String url,
                            Instant publishedAt, double engagement) {
}
