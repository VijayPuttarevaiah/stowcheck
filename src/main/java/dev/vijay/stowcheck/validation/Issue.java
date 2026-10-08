package dev.vijay.stowcheck.validation;

/**
 * One finding against a stowage plan.
 *
 * @param rule     stable rule id, e.g. "CHECK_DIGIT", used for metrics and filtering
 * @param cell     stowage cell the finding belongs to, or null for header-level findings
 * @param position 1-based segment position in the file, so a planner can jump straight to it
 */
public record Issue(String rule, Severity severity, String cell, String containerId,
                    int position, String message) {

    public enum Severity {
        /** The plan is wrong and would mislead the terminal if loaded as-is. */
        ERROR,
        /** Legal but suspicious; worth a planner's look before the vessel arrives. */
        WARNING
    }
}
