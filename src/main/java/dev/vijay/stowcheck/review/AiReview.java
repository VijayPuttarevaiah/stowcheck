package dev.vijay.stowcheck.review;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** An AI second opinion on one validation run. Advisory only; it never changes the run's status. */
@Entity
public class AiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long runId;
    private Instant createdAt;
    private String model;
    private long inputTokens;
    private long outputTokens;
    /** Findings beyond the per-review cap are not sent to the model. */
    private int notReviewed;

    @Lob
    private String carrierMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_review_finding", joinColumns = @JoinColumn(name = "review_id"))
    @OrderColumn(name = "seq")
    private List<ReviewedFinding> findings = new ArrayList<>();

    protected AiReview() {
    }

    public AiReview(long runId, Instant createdAt, String model, long inputTokens, long outputTokens,
                    int notReviewed, String carrierMessage, List<ReviewedFinding> findings) {
        this.runId = runId;
        this.createdAt = createdAt;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.notReviewed = notReviewed;
        this.carrierMessage = carrierMessage;
        this.findings = new ArrayList<>(findings);
    }

    public Long getId() { return id; }
    public long getRunId() { return runId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getModel() { return model; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public int getNotReviewed() { return notReviewed; }
    public String getCarrierMessage() { return carrierMessage; }
    public List<ReviewedFinding> getFindings() { return findings; }

    @Embeddable
    public static class ReviewedFinding {
        /** Index into the run's finding list, in file order. */
        private int findingIndex;
        private String rule;
        private String cell;
        private String containerId;
        @Enumerated(EnumType.STRING)
        private ModelReview.Verdict verdict;
        @Column(length = 1000)
        private String explanation;
        @Column(length = 1000)
        private String suggestedFix;

        protected ReviewedFinding() {
        }

        public ReviewedFinding(int findingIndex, String rule, String cell, String containerId,
                               ModelReview.Verdict verdict, String explanation, String suggestedFix) {
            this.findingIndex = findingIndex;
            this.rule = rule;
            this.cell = cell;
            this.containerId = containerId;
            this.verdict = verdict;
            this.explanation = explanation;
            this.suggestedFix = suggestedFix;
        }

        public int getFindingIndex() { return findingIndex; }
        public String getRule() { return rule; }
        public String getCell() { return cell; }
        public String getContainerId() { return containerId; }
        public ModelReview.Verdict getVerdict() { return verdict; }
        public String getExplanation() { return explanation; }
        public String getSuggestedFix() { return suggestedFix; }
    }
}
