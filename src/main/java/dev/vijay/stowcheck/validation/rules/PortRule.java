package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Every cell needs a port of loading (LOC+9) and a port of discharge (LOC+11), and both
 * should be UN/LOCODEs: 2-letter country plus 3 characters, e.g. CAHAL, USLGB.
 * Without a discharge port the terminal does not know whether to take the box off.
 */
@Component
public class PortRule implements Rule {

    private static final String UN_LOCODE = "[A-Z]{2}[A-Z2-9]{3}";

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();

        for (StowedUnit unit : plan.units()) {
            checkPort(unit, unit.portOfLoading(), "port of loading (LOC+9)", issues);
            checkPort(unit, unit.portOfDischarge(), "port of discharge (LOC+11)", issues);
        }
        return issues;
    }

    private static void checkPort(StowedUnit unit, String port, String label, List<Issue> issues) {
        if (port == null) {
            issues.add(Rule.error("MISSING_PORT", unit, "No " + label));
        } else if (!port.matches(UN_LOCODE)) {
            issues.add(Rule.warning("PORT_CODE", unit,
                    "'" + port + "' in " + label + " is not a UN/LOCODE"));
        }
    }
}
