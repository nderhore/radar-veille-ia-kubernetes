package fr.liveveille.radar.api.feedback;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "feedback")
public class FeedbackEntity {

    @Id
    private String term;

    @Column(nullable = false)
    private String verdict;

    private String analyst;

    @Column(length = 1000)
    private String comment;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected FeedbackEntity() {
    }

    public FeedbackEntity(String term, String verdict, String analyst, String comment, Instant decidedAt) {
        this.term = term;
        this.verdict = verdict;
        this.analyst = analyst;
        this.comment = comment;
        this.decidedAt = decidedAt;
    }

    public String getTerm() { return term; }
    public String getVerdict() { return verdict; }
    public String getAnalyst() { return analyst; }
    public String getComment() { return comment; }
    public Instant getDecidedAt() { return decidedAt; }
}
