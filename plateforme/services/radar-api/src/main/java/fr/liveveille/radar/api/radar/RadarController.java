package fr.liveveille.radar.api.radar;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.liveveille.radar.api.signal.SignalRepository;

/** API publique consommée par le front Angular (relayée par le nginx de radar-web). */
@RestController
@RequestMapping("/api")
public class RadarController {

    public record SignalSummary(String title, String source, String sourceType, String url, Instant publishedAt,
                                double engagement) {
    }

    private final RadarService radar;
    private final SignalRepository signals;

    public RadarController(RadarService radar, SignalRepository signals) {
        this.radar = radar;
        this.signals = signals;
    }

    @GetMapping("/radar")
    public RadarService.Snapshot radar() {
        return radar.current();
    }

    @GetMapping("/signals")
    public List<SignalSummary> latest(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return signals.findAllByOrderByPublishedAtDesc(PageRequest.of(0, limit)).stream()
                .map(s -> new SignalSummary(s.getTitle(), s.getSource(), s.getSourceType(), s.getUrl(),
                        s.getPublishedAt(), s.getEngagement()))
                .toList();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (Object[] row : signals.countBySource()) {
            bySource.put((String) row[0], (Long) row[1]);
        }
        return Map.of("total", signals.count(), "bySource", bySource);
    }
}
