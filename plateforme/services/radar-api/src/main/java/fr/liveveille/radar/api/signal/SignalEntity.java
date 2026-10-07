package fr.liveveille.radar.api.signal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signals")
public class SignalEntity {

    @Id
    @Column(length = 16)
    private String id;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 4000)
    private String summary;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    private double engagement;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    protected SignalEntity() {
    }

    public SignalEntity(String id, String source, String sourceType, String title, String summary, String url,
                        Instant publishedAt, double engagement, Instant collectedAt) {
        this.id = id;
        this.source = source;
        this.sourceType = sourceType;
        this.title = title;
        this.summary = summary;
        this.url = url;
        this.publishedAt = publishedAt;
        this.engagement = engagement;
        this.collectedAt = collectedAt;
    }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getSourceType() { return sourceType; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getUrl() { return url; }
    public Instant getPublishedAt() { return publishedAt; }
    public double getEngagement() { return engagement; }
    public Instant getCollectedAt() { return collectedAt; }

    public void setEngagement(double engagement) { this.engagement = engagement; }
}
