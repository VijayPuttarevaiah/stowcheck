package dev.vijay.stowcheck.review;

import java.util.List;

/**
 * The JSON shape the model must return. {@link GroqReviewModel} sends the matching JSON
 * schema in strict mode, so the response is guaranteed to parse into these records.
 */
public record ModelReview(List<Finding> findings, String carrierMessage) {

    public enum Verdict {
        /** The finding is a real data error the sender must correct. */
        CONFIRMED,
        /** The data is unusual but the segments suggest it was deliberate (e.g. a non-running reefer). */
        LIKELY_INTENTIONAL,
        /** The segments do not say enough either way; a planner has to look. */
        NEEDS_HUMAN
    }

    public record Finding(int index, Verdict verdict, String explanation, String suggestedFix) {
    }
}
