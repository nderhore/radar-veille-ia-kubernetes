package fr.liveveille.radar.collector.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Plan de sourcing : requêtes par source, cadence et profondeur d'historique. */
@Validated
@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(
        @NotBlank String cron,
        @NotBlank String apiUrl,
        @Positive int lookbackDays,
        boolean runOnStartup,
        Source hackernews,
        Source arxiv,
        Source github) {

    /**
     * @param minEngagement seuil de bruit : points Hacker News ou étoiles GitHub
     * @param maxResults    plafond par requête (le contrôle de couverture côté API détecte une troncature)
     */
    public record Source(boolean enabled, List<String> queries, int minEngagement, int maxResults) {
    }
}
