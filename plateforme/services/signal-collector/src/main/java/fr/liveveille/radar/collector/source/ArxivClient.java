package fr.liveveille.radar.collector.source;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import fr.liveveille.radar.collector.config.CollectorProperties;

/** arXiv (flux Atom) : signal académique, très amont dans le cycle de vie (TRL 1–3). */
@Component
public class ArxivClient implements SourceClient {

    private static final Logger log = LoggerFactory.getLogger(ArxivClient.class);
    private static final String ATOM = "http://www.w3.org/2005/Atom";

    private final RestClient http;
    private final CollectorProperties.Source conf;

    public ArxivClient(RestClient.Builder builder, CollectorProperties props) {
        this.http = HttpClients.build(builder, "https://export.arxiv.org");
        this.conf = props.arxiv();
    }

    @Override
    public String name() {
        return "arxiv";
    }

    @Override
    public boolean enabled() {
        return conf != null && conf.enabled();
    }

    @Override
    public List<SignalPayload> fetch(Instant since) {
        List<SignalPayload> all = new ArrayList<>();
        for (String query : conf.queries()) {
            String xml = http.get()
                    .uri(u -> u.path("/api/query")
                            .queryParam("search_query", "{q}")
                            .queryParam("sortBy", "submittedDate")
                            .queryParam("sortOrder", "descending")
                            .queryParam("max_results", conf.maxResults())
                            .build(Map.of("q", query)))
                    .retrieve()
                    .body(String.class);
            all.addAll(parse(xml, since));
            sleepQuietly(3_000);    // conditions d'utilisation d'arXiv : une requête toutes les 3 secondes
        }
        return all;
    }

    /**
     * Analyse le flux Atom. L'analyseur est durci contre les attaques XXE : un flux externe
     * est une donnée non fiable, au même titre qu'une saisie utilisateur.
     */
    static List<SignalPayload> parse(String xml, Instant since) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList entries = document.getElementsByTagNameNS(ATOM, "entry");
            List<SignalPayload> signals = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                Instant published = Instant.parse(text(entry, "published"));
                if (published.isBefore(since)) {
                    break;  // flux trié par date décroissante
                }
                signals.add(new SignalPayload("arxiv", "academic", clean(text(entry, "title")),
                        truncate(clean(text(entry, "summary")), 4000), text(entry, "id").trim(), published, 0));
            }
            return signals;
        } catch (Exception e) {
            throw new IllegalArgumentException("flux arXiv illisible : " + e.getMessage(), e);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagNameNS(ATOM, tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("attente interrompue");
        }
    }
}
