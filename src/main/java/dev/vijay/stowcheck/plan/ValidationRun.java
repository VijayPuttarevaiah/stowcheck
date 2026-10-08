package dev.vijay.stowcheck.plan;

import dev.vijay.stowcheck.validation.Issue;
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

/**
 * One uploaded stowage plan and its findings. The raw EDI is kept so the plan can be
 * re-parsed later for the bay view or compared against a newer version.
 */
@Entity
public class ValidationRun {

    public enum Status { ACCEPTED, ACCEPTED_WITH_WARNINGS, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant receivedAt;
    private String sender;
    private String carrier;
    private String vesselName;
    private String voyage;
    private String messageRef;
    private int unitCount;
    private int errorCount;
    private int warningCount;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Lob
    @Column(nullable = false)
    private String rawMessage;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "validation_issue", joinColumns = @JoinColumn(name = "run_id"))
    @OrderColumn(name = "seq")
    private List<IssueRecord> issues = new ArrayList<>();

    protected ValidationRun() {
    }

    public ValidationRun(String rawMessage, Instant receivedAt) {
        this.rawMessage = rawMessage;
        this.receivedAt = receivedAt;
    }

    public void recordHeader(String sender, String carrier, String vesselName, String voyage,
                             String messageRef, int unitCount) {
        this.sender = sender;
        this.carrier = carrier;
        this.vesselName = vesselName;
        this.voyage = voyage;
        this.messageRef = messageRef;
        this.unitCount = unitCount;
    }

    public void recordIssues(List<Issue> found) {
        issues.clear();
        found.forEach(i -> issues.add(IssueRecord.from(i)));
        errorCount = (int) found.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count();
        warningCount = found.size() - errorCount;
        status = errorCount > 0 ? Status.REJECTED
                : warningCount > 0 ? Status.ACCEPTED_WITH_WARNINGS
                : Status.ACCEPTED;
    }

    public Long getId() { return id; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getSender() { return sender; }
    public String getCarrier() { return carrier; }
    public String getVesselName() { return vesselName; }
    public String getVoyage() { return voyage; }
    public String getMessageRef() { return messageRef; }
    public int getUnitCount() { return unitCount; }
    public int getErrorCount() { return errorCount; }
    public int getWarningCount() { return warningCount; }
    public Status getStatus() { return status; }
    public String getRawMessage() { return rawMessage; }
    public List<IssueRecord> getIssues() { return issues; }

    @Embeddable
    public static class IssueRecord {
        private String rule;
        @Enumerated(EnumType.STRING)
        private Issue.Severity severity;
        private String cell;
        private String containerId;
        private int position;
        @Column(length = 500)
        private String message;

        protected IssueRecord() {
        }

        static IssueRecord from(Issue issue) {
            IssueRecord r = new IssueRecord();
            r.rule = issue.rule();
            r.severity = issue.severity();
            r.cell = issue.cell();
            r.containerId = issue.containerId();
            r.position = issue.position();
            r.message = issue.message();
            return r;
        }

        public Issue toIssue() {
            return new Issue(rule, severity, cell, containerId, position, message);
        }
    }
}
