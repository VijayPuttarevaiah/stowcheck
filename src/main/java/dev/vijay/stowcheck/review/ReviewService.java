package dev.vijay.stowcheck.review;

import dev.vijay.stowcheck.baplie.BaplieParser;
import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.edifact.EdifactTokenizer;
import dev.vijay.stowcheck.edifact.Segment;
import dev.vijay.stowcheck.plan.PlanService;
import dev.vijay.stowcheck.plan.ValidationRun;
import dev.vijay.stowcheck.validation.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the optional AI review of a validation run.
 *
 * <p>The model only gives a second opinion: a verdict, explanation and suggested fix per
 * finding, plus a draft email to the carrier. Its answer is reconciled against the real
 * findings before it is stored. Entries for findings that do not exist are dropped, and
 * findings the model skipped are filled in as NEEDS_HUMAN. Nothing here changes a run's
 * status, which the rules alone decide.
 */
@Service
public class ReviewService {

    private final ObjectProvider<ReviewModel> model;
    private final PlanService plans;
    private final AiReviewRepository reviews;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final int maxFindings;

    public ReviewService(ObjectProvider<ReviewModel> model, PlanService plans, AiReviewRepository reviews,
                         MeterRegistry metrics, Clock clock,
                         @Value("${stowcheck.ai.max-findings:40}") int maxFindings) {
        this.model = model;
        this.plans = plans;
        this.reviews = reviews;
        this.metrics = metrics;
        this.clock = clock;
        this.maxFindings = maxFindings;
    }

    public boolean enabled() {
        return model.getIfAvailable() != null;
    }

    public Optional<AiReview> latest(long runId) {
        plans.get(runId); // 404 if the run does not exist
        return reviews.findTopByRunIdOrderByCreatedAtDesc(runId);
    }

    public AiReview review(long runId) {
        ReviewModel reviewer = model.getIfAvailable();
        if (reviewer == null) {
            throw new ReviewUnavailableException("AI review is off. Set GROQ_API_KEY to turn it on.");
        }

        ValidationRun run = plans.get(runId);
        List<Issue> all = run.getIssues().stream().map(ValidationRun.IssueRecord::toIssue).toList();
        if (all.isEmpty()) {
            throw new ReviewUnavailableException("This plan has no findings to review");
        }
        List<Issue> issues = all.subList(0, Math.min(all.size(), maxFindings));

        String prompt = buildPrompt(run, issues);
        ReviewModel.Result result;
        try {
            result = reviewer.review(prompt);
        } catch (ReviewUnavailableException e) {
            metrics.counter("stowcheck.ai.reviews", "outcome", "failed").increment();
            throw e;
        }

        List<AiReview.ReviewedFinding> reconciled = reconcile(issues, result.review());
        AiReview review = reviews.save(new AiReview(runId, Instant.now(clock), result.model(),
                result.inputTokens(), result.outputTokens(), all.size() - issues.size(),
                result.review().carrierMessage(), reconciled));

        metrics.counter("stowcheck.ai.reviews", "outcome", "completed").increment();
        metrics.counter("stowcheck.ai.tokens", "type", "input").increment(result.inputTokens());
        metrics.counter("stowcheck.ai.tokens", "type", "output").increment(result.outputTokens());
        for (AiReview.ReviewedFinding f : reconciled) {
            metrics.counter("stowcheck.ai.verdicts", "verdict", f.getVerdict().name().toLowerCase(),
                    "rule", f.getRule()).increment();
        }
        return review;
    }

    static List<AiReview.ReviewedFinding> reconcile(List<Issue> issues, ModelReview answer) {
        Map<Integer, ModelReview.Finding> byIndex = new HashMap<>();
        if (answer.findings() != null) {
            for (ModelReview.Finding f : answer.findings()) {
                if (f.index() >= 0 && f.index() < issues.size() && f.verdict() != null) {
                    byIndex.putIfAbsent(f.index(), f);
                }
            }
        }

        List<AiReview.ReviewedFinding> out = new ArrayList<>();
        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            ModelReview.Finding f = byIndex.get(i);
            out.add(f == null
                    ? new AiReview.ReviewedFinding(i, issue.rule(), issue.cell(), issue.containerId(),
                            ModelReview.Verdict.NEEDS_HUMAN, "The model did not return a verdict for this finding.", "")
                    : new AiReview.ReviewedFinding(i, issue.rule(), issue.cell(), issue.containerId(),
                            f.verdict(), f.explanation(), f.suggestedFix()));
        }
        return out;
    }

    private static String buildPrompt(ValidationRun run, List<Issue> issues) {
        List<Segment> segments = EdifactTokenizer.tokenize(run.getRawMessage());
        StowagePlan plan = BaplieParser.parse(run.getRawMessage());

        // each cell's segment group runs from its LOC+147 to the segment before the next one
        Map<Integer, StowedUnit> byPosition = new HashMap<>();
        Map<Integer, Integer> groupEnd = new HashMap<>();
        List<StowedUnit> units = plan.units();
        int trailer = segments.size();
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i).tag().equals("UNT")) {
                trailer = i; // 0-based index of UNT == 1-based position of the segment before it
                break;
            }
        }
        for (int i = 0; i < units.size(); i++) {
            StowedUnit unit = units.get(i);
            byPosition.put(unit.position(), unit);
            groupEnd.put(unit.position(), i + 1 < units.size() ? units.get(i + 1).position() - 1 : trailer);
        }
        return ReviewPrompt.build(run, issues, segments, byPosition, groupEnd);
    }
}
