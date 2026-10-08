package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reefer checks.
 *
 * <p>Per the SMDG manual, a reefer sent without TMP is a "dry reefer" (unit not running).
 * That is legal, but a full reefer with no set point is the classic way a running reefer
 * gets unplugged, so it is flagged for a planner to confirm. A set point on a non-reefer
 * means the ISO type or the temperature is wrong.
 */
@Component
public class ReeferRule implements Rule {

    private static final double MIN_SET_POINT_C = -40;
    private static final double MAX_SET_POINT_C = 30;

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();

        for (StowedUnit unit : plan.units()) {
            if (!unit.isContainer()) {
                continue;
            }
            Double temp = unit.temperatureC();

            if (unit.isReefer() && unit.isFull() && temp == null) {
                issues.add(Rule.warning("REEFER_NO_TEMPERATURE", unit,
                        "Full reefer has no TMP set point, so it will be treated as non-running"));
            }
            if (temp != null && !unit.isReefer()) {
                issues.add(Rule.error("TEMPERATURE_ON_NON_REEFER", unit,
                        "Temperature " + temp + " C sent for ISO type " + unit.isoSizeType()
                                + ", which is not a reefer"));
            }
            if (temp != null && (temp < MIN_SET_POINT_C || temp > MAX_SET_POINT_C)) {
                issues.add(Rule.warning("REEFER_SET_POINT", unit,
                        "Set point " + temp + " C is outside the usual " + (int) MIN_SET_POINT_C
                                + " to " + (int) MAX_SET_POINT_C + " C range"));
            }
        }
        return issues;
    }
}
