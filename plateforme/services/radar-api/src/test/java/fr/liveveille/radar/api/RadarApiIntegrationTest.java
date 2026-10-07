package fr.liveveille.radar.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Démarre l'application complète sur H2 (mode PostgreSQL) : migrations Flyway, JPA, MVC, validation. */
@SpringBootTest
@ActiveProfiles("test")
class RadarApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String signal(String source, String type, String title, String url, Instant at) {
        return """
                {"source":"%s","sourceType":"%s","title":"%s","summary":"","url":"%s","publishedAt":"%s","engagement":3}
                """.formatted(source, type, title, url, at);
    }

    @Test
    void ingestIsIdempotentAndFeedsTheRadar() throws Exception {
        Instant now = Instant.now();
        String batch = "[" + signal("hackernews", "community", "MCP server for Postgres", "https://ex.org/a", now.minus(2, ChronoUnit.DAYS))
                + "," + signal("arxiv", "academic", "Model Context Protocol security", "https://arxiv.org/abs/1", now.minus(40, ChronoUnit.DAYS))
                + "]";

        mvc.perform(post("/internal/signals").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2));
        mvc.perform(post("/internal/signals").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0));        // dédoublonnage

        mvc.perform(get("/api/stats")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(2)));
        mvc.perform(get("/api/radar")).andExpect(status().isOk())
                .andExpect(jsonPath("$.topics").isArray())
                .andExpect(jsonPath("$.protocolVersion").value("2.0.0"))
                .andExpect(jsonPath("$.coverage.length()").value(5))            // 5 axes du protocole
                .andExpect(jsonPath("$.coverage[1].id").value("KIT-2"));
        mvc.perform(get("/api/protocole")).andExpect(status().isOk())
                .andExpect(jsonPath("$.axes[0].kiq[0].id").value("KIQ-1.1"))
                .andExpect(jsonPath("$.anneaux.agir.verbe").value("Décider"));
        mvc.perform(get("/api/signals").param("limit", "10")).andExpect(status().isOk());
    }

    @Test
    void invalidPayloadsAreRejectedWithProblemDetails() throws Exception {
        String bad = "[" + signal("x", "blog", "t", "ftp://nope", Instant.now()) + "]";
        mvc.perform(post("/internal/signals").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"rag\",\"verdict\":\"peut-etre\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/feedback").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"RAG\",\"verdict\":\"bruit\",\"analyst\":\"J. Martin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.term").value("rag"));
    }
}
