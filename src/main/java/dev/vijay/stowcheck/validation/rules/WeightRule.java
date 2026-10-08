package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gross mass checks. Weight drives vessel stability and crane limits, so a missing or
 * implausible weight is an error.
 *
 * <p>Since the SOLAS amendment of July 2016, a full export container needs a verified
 * gross mass (VGM) before loading. BAPLIE signals that with MEA+VGM instead of MEA+WT,
 * so a full container still on WT gets a warning.
 */
@Component
public class WeightRule implements Rule {

    private final int maxGrossKg;

    public WeightRule(@Value("${stowcheck.rules.max-gross-kg:36000}") int maxGrossKg) {
        this.maxGrossKg = maxGrossKg;
    }

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();

        for (StowedUnit unit : plan.units()) {
            if (!unit.isContainer()) {
                continue;
            }
            Integer kg = unit.weightKg();
            if (kg == null) {
                issues.add(Rule.error("MISSING_WEIGHT", unit, "No MEA gross weight for this container"));
                continue;
            }
            if (kg <= 0 && unit.isFull()) {
                issues.add(Rule.error("WEIGHT_RANGE", unit, "Full container with weight " + kg + " kg"));
            } else if (kg > maxGrossKg) {
                issues.add(Rule.error("WEIGHT_RANGE", unit,
                        kg + " kg is above the " + maxGrossKg + " kg limit"));
            }
            if (unit.isFull() && !unit.weightVerified()) {
                issues.add(Rule.warning("VGM_MISSING", unit,
                        "Full container weight is not marked as verified gross mass (MEA+VGM)"));
            }
        }
        return issues;
    }
}
