package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.ContainerNumbers;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container identity: every cell needs an EQD, every container number must pass the
 * ISO 6346 check digit, and no container can be in two cells at once.
 *
 * <p>A bad check digit usually means a typo upstream. Left alone, the terminal operating
 * system either rejects the unit or creates a phantom container that nobody can find.
 */
@Component
public class ContainerIdRule implements Rule {

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();
        Map<String, StowedUnit> seen = new HashMap<>();

        for (StowedUnit unit : plan.units()) {
            if (unit.equipmentType() == null) {
                issues.add(Rule.error("MISSING_EQUIPMENT", unit,
                        "Cell has no EQD segment, so the unit in it is unidentified"));
                continue;
            }
            if (!unit.isContainer() || unit.containerId() == null) {
                continue;
            }

            String id = unit.containerId();
            if (!ContainerNumbers.hasValidFormat(id)) {
                issues.add(Rule.error("CONTAINER_FORMAT", unit,
                        id + " is not an ISO 6346 container number (AAAU1234567)"));
            } else if (!ContainerNumbers.hasValidCheckDigit(id)) {
                issues.add(Rule.error("CHECK_DIGIT", unit,
                        id + " fails the ISO 6346 check digit, expected "
                                + ContainerNumbers.checkDigit(id) + " as the last digit"));
            }

            StowedUnit first = seen.putIfAbsent(id, unit);
            if (first != null) {
                issues.add(Rule.error("DUPLICATE_CONTAINER", unit,
                        id + " is also stowed in cell " + first.cell()));
            }
        }
        return issues;
    }
}
