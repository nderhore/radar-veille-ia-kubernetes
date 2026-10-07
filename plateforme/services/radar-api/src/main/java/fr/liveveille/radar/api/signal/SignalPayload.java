package fr.liveveille.radar.api.signal;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Signal transmis par le collecteur (contrat de l'API interne). */
public record SignalPayload(
        @NotBlank @Size(max = 100) String source,
        @NotBlank @Pattern(regexp = "academic|code|community|press|regulatory") String sourceType,
        @NotBlank @Size(max = 1000) String title,
        @Size(max = 4000) String summary,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "https?://.+") String url,
        @NotNull Instant publishedAt,
        @PositiveOrZero double engagement) {
}
