package dev.vijay.stowcheck.validation;

import dev.vijay.stowcheck.baplie.StowagePlan;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Runs every {@link Rule} bean against a plan and returns the findings in file order. */
@Component
public class PlanValidator {

    private final List<Rule> rules;

    public PlanValidator(List<Rule> rules) {
        this.rules = rules;
    }

    public List<Issue> validate(StowagePlan plan) {
        return rules.stream()
                .flatMap(rule -> rule.check(plan).stream())
                .sorted(Comparator.comparingInt(Issue::position).thenComparing(Issue::rule))
                .toList();
    }
}
