package fr.liveveille.radar.api.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Paramètres du radar et protocole de veille stratégique, injectés depuis la configuration
 * (ConfigMap en production). Le protocole est versionné hors du code : le réviser ne demande
 * pas de nouvelle image, et sa cohérence est vérifiée au démarrage.
 */
@Validated
@ConfigurationProperties(prefix = "radar")
public record RadarProperties(
        @Positive int recentDays,
        @Positive int baselineDays,
        @Positive double g2Threshold,
        Duration recomputeInterval,
        Weights weights,
        Map<String, Double> credibility,
        @Valid Protocol protocole) {

    public RadarProperties {
        if (baselineDays <= recentDays) {
            throw new IllegalArgumentException("la fenêtre de référence doit être plus longue que la fenêtre récente");
        }
        if (weights != null && Math.abs(weights.momentum() + weights.diffusion() + weights.impact() + weights.novelty() - 1.0) > 1e-6) {
            throw new IllegalArgumentException("les poids de l'indice de rupture doivent sommer à 1");
        }
        if (protocole != null) {
            protocole.validate();
        }
    }

    /** Pondération de l'indice de rupture (identique au radar Python du live). */
    public record Weights(double momentum, double diffusion, double impact, double novelty) {
    }

    /** Protocole de veille : finalité, axes (KIT), questions (KIQ), consignes par anneau, technologies suivies. */
    public record Protocol(
            @NotBlank String version,
            @NotBlank String finalite,
            @NotBlank String commanditaire,
            @NotEmpty List<@Valid Axis> axes,
            @NotEmpty Map<String, RingPolicy> anneaux,
            @NotEmpty List<@Valid Technology> technologies) {

        void validate() {
            Set<String> required = Set.of("agir", "preparer", "explorer", "surveiller");
            if (!anneaux.keySet().containsAll(required)) {
                throw new IllegalArgumentException("le protocole doit définir les anneaux " + required);
            }
            Map<String, Axis> byId = axes.stream().collect(Collectors.toMap(Axis::id, a -> a));
            for (Technology t : technologies) {
                Axis axis = byId.get(t.axe());
                if (axis == null) {
                    throw new IllegalArgumentException("technologie « " + t.label() + " » : axe inconnu " + t.axe());
                }
                if (axis.kiq().stream().noneMatch(k -> k.id().equals(t.kiq()))) {
                    throw new IllegalArgumentException("technologie « " + t.label() + " » : KIQ " + t.kiq() + " absente de " + t.axe());
                }
                if (!Set.of("opportunite", "menace", "mixte").contains(t.nature())) {
                    throw new IllegalArgumentException("technologie « " + t.label() + " » : nature invalide " + t.nature());
                }
            }
        }

        public Optional<Axis> axis(String id) {
            return axes.stream().filter(a -> a.id().equals(id)).findFirst();
        }
    }

    /** Axe de surveillance (Key Intelligence Topic), représenté par un quadrant du radar. */
    public record Axis(@NotBlank String id, @NotBlank String quadrant, @NotBlank String intitule,
                       @NotBlank String enjeu, @NotEmpty List<@Valid Kiq> kiq) {

        public Optional<Kiq> kiq(String id) {
            return kiq.stream().filter(k -> k.id().equals(id)).findFirst();
        }
    }

    /** Question décisionnelle (Key Intelligence Question) : rattachée à une décision, un responsable, un horizon. */
    public record Kiq(@NotBlank String id, @NotBlank String question, @NotBlank String decision,
                      @NotBlank String proprietaire, @NotBlank String horizon) {
    }

    /** Consigne associée à un anneau du radar : ce qu'il faut faire, et sous quel délai revoir le sujet. */
    public record RingPolicy(@NotBlank String libelle, @NotBlank String horizon, @NotBlank String verbe,
                             @NotBlank String consigne, @Positive int revueJours) {
    }

    /** Technologie suivie : rattachée à un axe et à une KIQ, avec l'enjeu qu'elle représente. */
    public record Technology(@NotBlank String label, @NotBlank String axe, @NotBlank String kiq,
                             @NotBlank String nature, @NotBlank String opportunite,
                             @NotEmpty List<String> synonyms) {
    }
}
