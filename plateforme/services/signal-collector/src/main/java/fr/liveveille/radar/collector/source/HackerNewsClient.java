package fr.liveveille.radar.collector.source;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import fr.liveveille.radar.collector.config.CollectorProperties;

/** Hacker News via l'API Algolia : signal communautaire précoce, filtré par un seuil de points. */
@Component
public class HackerNewsClient implements SourceClient {

    record Hit(String objectID, String title, String url, @JsonProperty("created_at_i") long createdAt,
               Integer points, @JsonProperty("num_comments") Integer comments) {
    }

    record Response(List<Hit> hits) {
    }

    private final RestClient http;
    private final CollectorProperties.Source conf;

    public HackerNewsClient(RestClient.Builder builder, CollectorProperties props) {
        this.http = HttpClients.build(builder, "https://hn.algolia.com");
        this.conf = props.hackernews();
    }

    @Override
    public String name() {
        return "hackernews";
    }

    @Override
    public boolean enabled() {
        return conf != null && conf.enabled();
    }

    @Override
    public List<SignalPayload> fetch(Instant since) {
        Map<String, SignalPayload> unique = new LinkedHashMap<>();
        for (String query : conf.queries()) {
            Response response = http.get()
                    .uri(u -> u.path("/api/v1/search_by_date")
                            .queryParam("query", "{q}")
                            .queryParam("tags", "story")
                            .queryParam("numericFilters", "created_at_i>" + since.getEpochSecond()
                                    + ",points>=" + conf.minEngagement())
                            .queryParam("hitsPerPage", conf.maxResults())
                            .build(Map.of("q", query)))
                    .retrieve()
                    .body(Response.class);
            toSignals(response).forEach(s -> unique.putIfAbsent(s.url(), s));   // dédoublonnage entre requêtes
        }
        return List.copyOf(unique.values());
    }

    static List<SignalPayload> toSignals(Response response) {
        if (response == null || response.hits() == null) {
            return List.of();
        }
        return response.hits().stream()
                .filter(h -> h.title() != null && !h.title().isBlank())
                .map(h -> new SignalPayload("hackernews", "community", h.title(), "",
                        h.url() != null && !h.url().isBlank() ? h.url() : "https://news.ycombinator.com/item?id=" + h.objectID(),
                        Instant.ofEpochSecond(h.createdAt()),
                        (h.points() == null ? 0 : h.points()) + 0.5 * (h.comments() == null ? 0 : h.comments())))
                .toList();
    }
}
