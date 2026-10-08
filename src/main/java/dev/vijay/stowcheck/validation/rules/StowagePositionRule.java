package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stowage cell checks.
 *
 * <ul>
 *   <li>Cell format: ISO cells are 7 digits, bay-row-tier (BBBRRTT).</li>
 *   <li>Cell conflict: two units in one cell. The SMDG manual allows this only for
 *       platforms or flat racks and for half-height containers, so a conflict involving a
 *       flat rack is a warning and anything else is an error.</li>
 *   <li>Bay parity: by convention 20ft units sit in odd bays and 40ft or 45ft units in
 *       the even bay that spans two odd ones. A 40ft box in bay 13 is almost certainly
 *       a planning error.</li>
 * </ul>
 */
@Component
public class StowagePositionRule implements Rule {

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();
        Map<String, StowedUnit> occupied = new HashMap<>();

        for (StowedUnit unit : plan.units()) {
            if (unit.cell() == null) {
                issues.add(Rule.error("CELL_FORMAT", unit, "LOC+147 has no stowage cell"));
                continue;
            }
            boolean isoFormat = unit.cellFormat().isEmpty() || "5".equals(unit.cellFormat());
            if (isoFormat && !unit.isIsoCell()) {
                issues.add(Rule.error("CELL_FORMAT", unit,
                        "Cell '" + unit.cell() + "' is not ISO bay-row-tier format (BBBRRTT)"));
                continue;
            }

            StowedUnit other = occupied.putIfAbsent(unit.cell(), unit);
            if (other != null) {
                boolean flatRacks = unit.isFlatOrPlatform() && other.isFlatOrPlatform();
                String message = "Cell " + unit.cell() + " is already taken by "
                        + describe(other);
                issues.add(flatRacks
                        ? Rule.warning("CELL_CONFLICT", unit, message + " (flat racks; confirm they are bundled)")
                        : Rule.error("CELL_CONFLICT", unit, message));
            }

            if (unit.isIsoCell()) {
                checkBayParity(unit, issues);
            }
        }
        return issues;
    }

    private static void checkBayParity(StowedUnit unit, List<Issue> issues) {
        int length = unit.lengthFeet();
        if (length == 0) {
            return;
        }
        boolean oddBay = unit.bay() % 2 == 1;
        if (length == 20 && !oddBay) {
            issues.add(Rule.warning("BAY_PARITY", unit,
                    "20ft unit in even bay " + unit.bay() + "; 20ft units normally use odd bays"));
        } else if (length > 20 && oddBay) {
            issues.add(Rule.error("BAY_PARITY", unit,
                    length + "ft unit in odd bay " + unit.bay()
                            + "; 40ft and 45ft units are stowed in even bays"));
        }
    }

    private static String describe(StowedUnit unit) {
        return unit.containerId() != null ? unit.containerId() : "segment " + unit.position();
    }
}
