package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Dangerous goods (DGS segment) checks: the IMDG class must look like a real class
 * (1 to 9, optional division such as 2.1 or 4.3), and the UN number should be present
 * and 4 digits. The UN number is optional in the SMDG manual, but without it the
 * terminal cannot apply segregation rules, so its absence is a warning.
 */
@Component
public class DangerousGoodsRule implements Rule {

    private static final String IMDG_CLASS = "[1-9](\\.[1-6])?[A-L]?";

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();

        for (StowedUnit unit : plan.units()) {
            for (StowedUnit.DangerousGood dg : unit.dangerousGoods()) {
                if (dg.imdgClass() == null || !dg.imdgClass().matches(IMDG_CLASS)) {
                    issues.add(Rule.error("DG_CLASS", unit,
                            "DGS segment " + dg.position() + " has invalid IMDG class '"
                                    + dg.imdgClass() + "'"));
                }
                if (dg.unNumber() == null) {
                    issues.add(Rule.warning("DG_UN_NUMBER", unit,
                            "DGS segment " + dg.position() + " has no UN number"));
                } else if (!dg.unNumber().matches("\\d{4}")) {
                    issues.add(Rule.error("DG_UN_NUMBER", unit,
                            "UN number '" + dg.unNumber() + "' must be 4 digits"));
                }
            }
        }
        return issues;
    }
}
