package fr.liveveille.radar.collector.source;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import fr.liveveille.radar.collector.config.CollectorProperties;

/** GitHub Search : signal d'adoption par les développeurs (TRL 4–6). */
@Component
public class GitHubClient implements SourceClient {

    record Repo(@JsonProperty("full_name") String fullName, String description,
                @JsonProperty("html_url") String htmlUrl, @JsonProperty("created_at") Instant createdAt,
                @JsonProperty("stargazers_count") int stars, List<String> topics) {
    }

    record Response(List<Repo> items) {
    }

    private final RestClient http;
    private final CollectorProperties.Source conf;

    public GitHubClient(RestClient.Builder builder, CollectorProperties props,
                        @Value("${GITHUB_TOKEN:}") String token) {
        RestClient.Builder b = builder.clone()
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (!token.isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + token);   // quota relevé : 30 requêtes / min
        }
        this.http = HttpClients.build(b, "https://api.github.com");
        this.conf = props.github();
    }

    @Override
    public String name() {
        return "github";
    }

    @Override
    public boolean enabled() {
        return conf != null && conf.enabled();
    }

    @Override
    public List<SignalPayload> fetch(Instant since) {
        String day = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(since);
        Map<String, SignalPayload> unique = new LinkedHashMap<>();
        for (String query : conf.queries()) {
            Response response = http.get()
                    .uri(u -> u.path("/search/repositories")
                            .queryParam("q", "{q}")
                            .queryParam("sort", "stars")
                            .queryParam("order", "desc")
                            .queryParam("per_page", conf.maxResults())
                            .build(Map.of("q", query + " created:>=" + day + " stars:>=" + conf.minEngagement())))
                    .retrieve()
                    .body(Response.class);
            toSignals(response).forEach(s -> unique.putIfAbsent(s.url(), s));
        }
        return List.copyOf(unique.values());
    }

    static List<SignalPayload> toSignals(Response response) {
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream()
                .map(r -> new SignalPayload("github", "code",
                        r.fullName() + (r.description() == null ? "" : " : " + r.description()),
                        r.topics() == null ? "" : String.join(" ", r.topics()),
                        r.htmlUrl(), r.createdAt(), r.stars()))
                .toList();
    }
}
