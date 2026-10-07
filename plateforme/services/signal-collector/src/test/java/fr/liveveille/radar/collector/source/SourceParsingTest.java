package fr.liveveille.radar.collector.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class SourceParsingTest {

    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>http://arxiv.org/abs/2610.00001v1</id>
                <published>2026-10-05T10:00:00Z</published>
                <title>On-policy   distillation
                  for small models</title>
                <summary>We study distillation.</summary>
              </entry>
              <entry>
                <id>http://arxiv.org/abs/2601.00002v1</id>
                <published>2026-01-01T10:00:00Z</published>
                <title>Old paper</title>
                <summary>Too old.</summary>
              </entry>
            </feed>
            """;

    @Test
    void arxivFeedIsParsedAndStopsAtTheLookbackLimit() {
        List<SignalPayload> signals = ArxivClient.parse(FEED, Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().title()).isEqualTo("On-policy distillation for small models");
        assertThat(signals.getFirst().sourceType()).isEqualTo("academic");
    }

    @Test
    void arxivParserRejectsExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE feed [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <feed xmlns="http://www.w3.org/2005/Atom"><entry><title>&secret;</title></entry></feed>
                """;
        assertThatThrownBy(() -> ArxivClient.parse(xxe, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hackerNewsHitsAreMappedWithFallbackUrlAndEngagement() {
        var response = new HackerNewsClient.Response(List.of(
                new HackerNewsClient.Hit("42", "Show HN: local LLM", null, 1_790_000_000L, 120, 40),
                new HackerNewsClient.Hit("43", "", "https://ex.org", 1_790_000_000L, 10, 0)));
        List<SignalPayload> signals = HackerNewsClient.toSignals(response);
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().url()).isEqualTo("https://news.ycombinator.com/item?id=42");
        assertThat(signals.getFirst().engagement()).isEqualTo(140.0);
    }

    @Test
    void gitHubRepositoriesBecomeCodeSignals() {
        var response = new GitHubClient.Response(List.of(new GitHubClient.Repo("acme/agent", "An agent framework",
                "https://github.com/acme/agent", Instant.parse("2026-10-01T00:00:00Z"), 250, List.of("mcp", "agents"))));
        SignalPayload s = GitHubClient.toSignals(response).getFirst();
        assertThat(s.sourceType()).isEqualTo("code");
        assertThat(s.summary()).isEqualTo("mcp agents");
        assertThat(s.engagement()).isEqualTo(250);
    }
}
