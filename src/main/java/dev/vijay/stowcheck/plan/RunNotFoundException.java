package dev.vijay.stowcheck.plan;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(long id) {
        super("No validation run with id " + id);
    }
}
