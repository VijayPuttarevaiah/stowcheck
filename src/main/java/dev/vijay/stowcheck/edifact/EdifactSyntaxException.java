package dev.vijay.stowcheck.edifact;

/** The text could not be read as EDIFACT at all, so no rule can run against it. */
public class EdifactSyntaxException extends RuntimeException {

    public EdifactSyntaxException(String message) {
        super(message);
    }
}
