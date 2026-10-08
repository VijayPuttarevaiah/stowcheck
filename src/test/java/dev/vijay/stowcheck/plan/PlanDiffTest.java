package dev.vijay.stowcheck.plan;

import dev.vijay.stowcheck.TestPlans;
import dev.vijay.stowcheck.baplie.BaplieParser;
import org.junit.jupiter.api.Test;

import static dev.vijay.stowcheck.plan.PlanDiff.Kind.ADDED;
import static dev.vijay.stowcheck.plan.PlanDiff.Kind.DISCHARGE_PORT_CHANGED;
import static dev.vijay.stowcheck.plan.PlanDiff.Kind.MOVED;
import static dev.vijay.stowcheck.plan.PlanDiff.Kind.REMOVED;
import static dev.vijay.stowcheck.plan.PlanDiff.Kind.WEIGHT_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PlanDiffTest {

    @Test
    void listsEveryChangeBetweenPreliminaryAndFinalPlan() {
        PlanDiff diff = PlanDiff.between(
                1, BaplieParser.parse(TestPlans.load("valid-plan.edi")),
                2, BaplieParser.parse(TestPlans.load("valid-plan-final.edi")));

        assertThat(diff.changes())
                .extracting(PlanDiff.Change::kind, PlanDiff.Change::containerId,
                        PlanDiff.Change::before, PlanDiff.Change::after)
                .containsExactlyInAnyOrder(
                        tuple(MOVED, "MAEU5551235", "0020084", "0040084"),
                        tuple(WEIGHT_CHANGED, "CMAU7654327", "29100 kg", "29350 kg"),
                        tuple(REMOVED, "ONEU9876541", "0100086", null),
                        tuple(ADDED, "OOLU4445558", null, "0080082"),
                        tuple(DISCHARGE_PORT_CHANGED, "TGHU1112229", "CAHAL", "USNYC"));
    }

    @Test
    void identicalPlansHaveNoChanges() {
        var plan = BaplieParser.parse(TestPlans.load("valid-plan.edi"));

        assertThat(PlanDiff.between(1, plan, 1, plan).changes()).isEmpty();
    }
}
