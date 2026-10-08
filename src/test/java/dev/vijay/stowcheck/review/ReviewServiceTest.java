package dev.vijay.stowcheck.review;

import dev.vijay.stowcheck.validation.Issue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vijay.stowcheck.review.ModelReview.Verdict.CONFIRMED;
import static dev.vijay.stowcheck.review.ModelReview.Verdict.LIKELY_INTENTIONAL;
import static dev.vijay.stowcheck.review.ModelReview.Verdict.NEEDS_HUMAN;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    private static final List<Issue> ISSUES = List.of(
            new Issue("CHECK_DIGIT", Issue.Severity.ERROR, "0010282", "MSCU1234560", 8, "bad digit"),
            new Issue("REEFER_NO_TEMPERATURE", Issue.Severity.WARNING, "0060082", "CMAU7654327", 38, "no TMP"),
            new Issue("MISSING_PORT", Issue.Severity.ERROR, "0090282", "TRIU5550002", 49, "no POD"));

    @Test
    void keepsOneVerdictPerRealFinding() {
        ModelReview answer = new ModelReview(List.of(
                new ModelReview.Finding(0, CONFIRMED, "Typo in the serial.", "Use MSCU1234566"),
                new ModelReview.Finding(1, LIKELY_INTENTIONAL, "FTX says not running.", ""),
                new ModelReview.Finding(2, CONFIRMED, "No LOC+11.", "Ask for the discharge port")), "msg");

        List<AiReview.ReviewedFinding> out = ReviewService.reconcile(ISSUES, answer);

        assertThat(out).extracting(AiReview.ReviewedFinding::getVerdict)
                .containsExactly(CONFIRMED, LIKELY_INTENTIONAL, CONFIRMED);
        assertThat(out.get(0).getContainerId()).isEqualTo("MSCU1234560");
        assertThat(out.get(0).getRule()).isEqualTo("CHECK_DIGIT");
    }

    @Test
    void dropsInventedFindingsAndFillsSkippedOnes() {
        ModelReview answer = new ModelReview(List.of(
                new ModelReview.Finding(0, CONFIRMED, "Typo.", "fix"),
                new ModelReview.Finding(0, LIKELY_INTENTIONAL, "duplicate entry, ignored", ""),
                new ModelReview.Finding(7, CONFIRMED, "this finding does not exist", ""),
                new ModelReview.Finding(-1, CONFIRMED, "neither does this one", "")), "msg");

        List<AiReview.ReviewedFinding> out = ReviewService.reconcile(ISSUES, answer);

        assertThat(out).hasSize(3);
        assertThat(out).extracting(AiReview.ReviewedFinding::getVerdict)
                .containsExactly(CONFIRMED, NEEDS_HUMAN, NEEDS_HUMAN);
        assertThat(out.get(1).getExplanation()).contains("did not return a verdict");
    }

    @Test
    void handlesAnEmptyAnswer() {
        List<AiReview.ReviewedFinding> out = ReviewService.reconcile(ISSUES, new ModelReview(null, ""));

        assertThat(out).extracting(AiReview.ReviewedFinding::getVerdict).containsOnly(NEEDS_HUMAN);
    }
}
