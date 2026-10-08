package dev.vijay.stowcheck.review;

/** Something that can review a prepared prompt. Groq in production, a fake in tests. */
public interface ReviewModel {

    Result review(String prompt);

    record Result(ModelReview review, String model, long inputTokens, long outputTokens) {
    }
}
