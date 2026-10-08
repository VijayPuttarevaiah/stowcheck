package dev.vijay.stowcheck.baplie;

import dev.vijay.stowcheck.edifact.EdifactSyntaxException;
import dev.vijay.stowcheck.edifact.EdifactTokenizer;
import dev.vijay.stowcheck.edifact.Segment;

import java.util.List;

/**
 * Maps EDIFACT segments onto a {@link StowagePlan}, following the SMDG BAPLIE 2.2.1
 * user manual (UN/EDIFACT D.95B).
 *
 * <p>The parser is deliberately lenient: it records what the sender transmitted and
 * leaves judgement to the validation rules. A missing weight becomes a null field and a
 * rule finding, not a parse failure. Only text that is not EDIFACT at all is rejected.
 */
public final class BaplieParser {

    private static final double KG_PER_LB = 0.45359237;

    private BaplieParser() {
    }

    public static StowagePlan parse(String raw) {
        List<Segment> segments = EdifactTokenizer.tokenize(raw);
        StowagePlan plan = new StowagePlan();

        StowedUnit unit = null;
        boolean insideMessage = false;

        for (Segment s : segments) {
            if (insideMessage) {
                plan.actualSegmentCount++;
            }

            switch (s.tag()) {
                case "UNB" -> {
                    plan.sender = s.value(1);
                    plan.recipient = s.value(2);
                    plan.interchangeRef = s.value(4);
                }
                case "UNH" -> {
                    plan.messageCount++;
                    if (plan.messageCount > 1) {
                        throw new EdifactSyntaxException(
                                "Only one BAPLIE message per interchange is supported");
                    }
                    insideMessage = true;
                    plan.actualSegmentCount = 1;
                    plan.messageRef = s.value(0);
                    plan.messageType = s.value(1, 0);
                    plan.messageVersion = s.value(1, 1) + s.value(1, 2);
                }
                case "BGM" -> plan.messageFunction = s.value(2);
                case "TDT" -> {
                    plan.voyage = s.value(1);
                    plan.carrier = s.value(4, 0);
                    plan.vesselId = s.value(7, 0);
                    plan.vesselName = s.value(7, 3);
                }
                case "LOC" -> unit = handleLoc(plan, unit, s);
                case "MEA" -> {
                    if (unit != null) {
                        readWeight(unit, s);
                    }
                }
                case "TMP" -> {
                    if (unit != null) {
                        unit.temperatureC = readTemperature(s);
                    }
                }
                case "EQD" -> {
                    if (unit != null) {
                        unit.equipmentType = s.value(0);
                        unit.containerId = blankToNull(s.value(1).replace(" ", ""));
                        unit.isoSizeType = blankToNull(s.value(2));
                        unit.fullEmpty = blankToNull(s.value(5));
                    }
                }
                case "NAD" -> {
                    if (unit != null && "CA".equals(s.value(0))) {
                        unit.operator = s.value(1, 0);
                    }
                }
                case "DGS" -> {
                    if (unit != null) {
                        unit.dangerousGoods.add(new StowedUnit.DangerousGood(
                                blankToNull(s.value(1, 0)), blankToNull(s.value(2, 0)),
                                s.position()));
                    }
                }
                case "UNT" -> {
                    insideMessage = false;
                    plan.declaredSegmentCount = parseIntOrNull(s.value(0));
                    plan.messageTrailerRef = s.value(1);
                }
                case "UNZ" -> {
                    plan.interchangeTrailerCount = parseIntOrNull(s.value(0));
                    plan.interchangeTrailerRef = s.value(1);
                }
                default -> {
                    // DTM, RFF, FTX, GID, GDS, DIM, RNG, EQA are accepted but not validated yet
                }
            }
        }

        if (plan.messageType == null) {
            throw new EdifactSyntaxException("No UNH message header found");
        }
        return plan;
    }

    /**
     * LOC is used three ways: 5/61 in the vessel header, 147 to open a new stowage cell,
     * and 9/11 for the cell's load and discharge ports.
     */
    private static StowedUnit handleLoc(StowagePlan plan, StowedUnit unit, Segment s) {
        String qualifier = s.value(0);
        String place = blankToNull(s.value(1, 0));

        switch (qualifier) {
            case "147" -> {
                StowedUnit next = new StowedUnit();
                next.position = s.position();
                next.cell = place;
                next.cellFormat = s.value(1, 2);
                plan.units.add(next);
                return next;
            }
            case "5" -> plan.departurePort = place;
            case "61" -> plan.nextPortOfCall = place;
            case "9" -> {
                if (unit != null) {
                    unit.portOfLoading = place;
                }
            }
            case "11" -> {
                if (unit != null) {
                    unit.portOfDischarge = place;
                }
            }
            default -> {
                // transshipment and optional ports are out of scope for now
            }
        }
        return unit;
    }

    private static void readWeight(StowedUnit unit, Segment s) {
        String qualifier = s.value(0);
        if (!"WT".equals(qualifier) && !"VGM".equals(qualifier)) {
            return;
        }
        Integer value = parseIntOrNull(s.value(2, 1));
        if (value == null) {
            return;
        }
        unit.weightVerified = "VGM".equals(qualifier);
        unit.weightKg = "LBR".equals(s.value(2, 0))
                ? (int) Math.round(value * KG_PER_LB)
                : value;
    }

    private static Double readTemperature(Segment s) {
        try {
            double value = Double.parseDouble(s.value(1, 0));
            return "FAH".equals(s.value(1, 1)) ? (value - 32) * 5 / 9 : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
