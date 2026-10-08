package dev.vijay.stowcheck.validation;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;

import java.util.List;

/** A single check against a parsed stowage plan. Each rule is a Spring bean. */
public interface Rule {

    List<Issue> check(StowagePlan plan);

    static Issue error(String rule, StowedUnit unit, String message) {
        return new Issue(rule, Issue.Severity.ERROR, unit.cell(), unit.containerId(),
                unit.position(), message);
    }

    static Issue warning(String rule, StowedUnit unit, String message) {
        return new Issue(rule, Issue.Severity.WARNING, unit.cell(), unit.containerId(),
                unit.position(), message);
    }
}
