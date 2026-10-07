package fr.liveveille.radar.collector.source;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Fabrique de clients HTTP sortants : délais explicites et User-Agent identifiable. */
public final class HttpClients {

    public static final String USER_AGENT = "radar-veille-ia/1.0 (usage pedagogique; contact: veille@example.org)";

    private HttpClients() {
    }

    public static RestClient build(RestClient.Builder builder, String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }
}
