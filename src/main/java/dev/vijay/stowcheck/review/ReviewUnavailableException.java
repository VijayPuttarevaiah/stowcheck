package dev.vijay.stowcheck.review;

/** The AI review could not run or did not return a usable answer. The rule findings still stand. */
public class ReviewUnavailableException extends RuntimeException {

    public ReviewUnavailableException(String message) {
        super(message);
    }

    public ReviewUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
