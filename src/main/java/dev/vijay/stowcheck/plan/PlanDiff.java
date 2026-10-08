package dev.vijay.stowcheck.plan;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What changed between two versions of a vessel's stowage plan, matched by container
 * number. Pre-planners receive a preliminary BAPLIE and later a final one; this lists
 * the containers that were added, removed, moved to another cell, re-weighed, or
 * re-routed to another discharge port.
 */
public record PlanDiff(long fromRunId, long toRunId, List<Change> changes) {

    public enum Kind { ADDED, REMOVED, MOVED, WEIGHT_CHANGED, DISCHARGE_PORT_CHANGED }

    public record Change(Kind kind, String containerId, String before, String after) {
    }

    public static PlanDiff between(long fromRunId, StowagePlan from, long toRunId, StowagePlan to) {
        Map<String, StowedUnit> before = byContainer(from);
        Map<String, StowedUnit> after = byContainer(to);
        List<Change> changes = new ArrayList<>();

        for (Map.Entry<String, StowedUnit> entry : before.entrySet()) {
            String id = entry.getKey();
            StowedUnit old = entry.getValue();
            StowedUnit now = after.get(id);

            if (now == null) {
                changes.add(new Change(Kind.REMOVED, id, old.cell(), null));
                continue;
            }
            if (!Objects.equals(old.cell(), now.cell())) {
                changes.add(new Change(Kind.MOVED, id, old.cell(), now.cell()));
            }
            if (!Objects.equals(old.weightKg(), now.weightKg())) {
                changes.add(new Change(Kind.WEIGHT_CHANGED, id,
                        kg(old.weightKg()), kg(now.weightKg())));
            }
            if (!Objects.equals(old.portOfDischarge(), now.portOfDischarge())) {
                changes.add(new Change(Kind.DISCHARGE_PORT_CHANGED, id,
                        old.portOfDischarge(), now.portOfDischarge()));
            }
        }
        for (Map.Entry<String, StowedUnit> entry : after.entrySet()) {
            if (!before.containsKey(entry.getKey())) {
                changes.add(new Change(Kind.ADDED, entry.getKey(), null, entry.getValue().cell()));
            }
        }
        return new PlanDiff(fromRunId, toRunId, changes);
    }

    /** First occurrence wins; duplicates are already reported by the validation rules. */
    private static Map<String, StowedUnit> byContainer(StowagePlan plan) {
        Map<String, StowedUnit> map = new LinkedHashMap<>();
        for (StowedUnit unit : plan.units()) {
            if (unit.isContainer() && unit.containerId() != null) {
                map.putIfAbsent(unit.containerId(), unit);
            }
        }
        return map;
    }

    private static String kg(Integer value) {
        return value == null ? null : value + " kg";
    }
}
