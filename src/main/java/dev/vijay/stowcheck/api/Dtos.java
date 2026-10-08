package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.baplie.StowedUnit;
import dev.vijay.stowcheck.plan.ValidationRun;
import dev.vijay.stowcheck.validation.Issue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JSON shapes returned by the API, kept apart from the JPA entity. */
final class Dtos {

    private Dtos() {
    }

    record Summary(long id, Instant receivedAt, String carrier, String vesselName, String voyage,
                   int units, int errors, int warnings, ValidationRun.Status status) {

        static Summary from(ValidationRun run) {
            return new Summary(run.getId(), run.getReceivedAt(), run.getCarrier(),
                    run.getVesselName(), run.getVoyage(), run.getUnitCount(),
                    run.getErrorCount(), run.getWarningCount(), run.getStatus());
        }
    }

    record Report(Summary summary, List<Issue> issues) {

        static Report from(ValidationRun run) {
            return new Report(Summary.from(run),
                    run.getIssues().stream().map(ValidationRun.IssueRecord::toIssue).toList());
        }
    }

    record Cell(String cell, int bay, int row, int tier, String containerId, String isoSizeType,
                Integer weightKg, String portOfDischarge, boolean reefer, boolean dangerous,
                Issue.Severity worst, List<String> messages) {

        static List<Cell> from(StowagePlan plan, List<ValidationRun.IssueRecord> records) {
            Map<Integer, List<Issue>> byPosition = new HashMap<>();
            for (ValidationRun.IssueRecord r : records) {
                Issue issue = r.toIssue();
                byPosition.computeIfAbsent(issue.position(), k -> new ArrayList<>()).add(issue);
            }

            List<Cell> cells = new ArrayList<>();
            for (StowedUnit unit : plan.units()) {
                List<Issue> issues = byPosition.getOrDefault(unit.position(), List.of());
                Issue.Severity worst = issues.stream().map(Issue::severity)
                        .min(Enum::compareTo).orElse(null);
                cells.add(new Cell(unit.cell(), unit.bay(), unit.row(), unit.tier(),
                        unit.containerId(), unit.isoSizeType(), unit.weightKg(),
                        unit.portOfDischarge(), unit.isReefer(), !unit.dangerousGoods().isEmpty(),
                        worst, issues.stream().map(Issue::message).toList()));
            }
            return cells;
        }
    }

    record Problem(String error) {
    }
}
