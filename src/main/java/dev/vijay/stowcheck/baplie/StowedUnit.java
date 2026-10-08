package dev.vijay.stowcheck.baplie;

import java.util.ArrayList;
import java.util.List;

/**
 * One occupied stowage cell: a BAPLIE segment group 2 (LOC+147 ... EQD ... DGS).
 *
 * <p>Fields are null when the sender left the segment out, so the rules can tell
 * "missing" apart from "present but wrong".
 */
public class StowedUnit {

    /** Position of the LOC+147 segment that opened this group, for error reports. */
    int position;

    /** Stowage cell, ISO format BBBRRTT, e.g. "0120082" = bay 012, row 00, tier 82. */
    String cell;
    /** "5" = ISO format, "87" = Ro/Ro, "ZZZ" = mutually defined. */
    String cellFormat;

    /** "CN" container, "BB" break-bulk, "TE" trailer. Null when no EQD was sent. */
    String equipmentType;
    String containerId;
    /** ISO 6346 size-type code, e.g. "45R1" = 40ft high-cube reefer. */
    String isoSizeType;
    /** "5" = full, "4" = empty. */
    String fullEmpty;

    /** Gross mass in kilograms, converted from pounds when the sender used LBR. */
    Integer weightKg;
    /** True when the MEA qualifier was VGM (verified gross mass) rather than WT. */
    boolean weightVerified;

    /** Transport temperature in Celsius, converted when the sender used FAH. */
    Double temperatureC;

    String portOfLoading;
    String portOfDischarge;
    String operator;

    final List<DangerousGood> dangerousGoods = new ArrayList<>();

    public int position() { return position; }
    public String cell() { return cell; }
    public String cellFormat() { return cellFormat; }
    public String equipmentType() { return equipmentType; }
    public String containerId() { return containerId; }
    public String isoSizeType() { return isoSizeType; }
    public String fullEmpty() { return fullEmpty; }
    public Integer weightKg() { return weightKg; }
    public boolean weightVerified() { return weightVerified; }
    public Double temperatureC() { return temperatureC; }
    public String portOfLoading() { return portOfLoading; }
    public String portOfDischarge() { return portOfDischarge; }
    public String operator() { return operator; }
    public List<DangerousGood> dangerousGoods() { return dangerousGoods; }

    public boolean isContainer() {
        return "CN".equals(equipmentType);
    }

    public boolean isFull() {
        return "5".equals(fullEmpty);
    }

    /** Bay number from an ISO cell, or -1 when the cell is not in ISO format. */
    public int bay() {
        return isIsoCell() ? Integer.parseInt(cell.substring(0, 3)) : -1;
    }

    public int row() {
        return isIsoCell() ? Integer.parseInt(cell.substring(3, 5)) : -1;
    }

    public int tier() {
        return isIsoCell() ? Integer.parseInt(cell.substring(5, 7)) : -1;
    }

    public boolean isIsoCell() {
        return cell != null && cell.matches("\\d{7}");
    }

    /**
     * Nominal length in feet from the first character of the ISO size-type code:
     * '2' = 20ft, '4' = 40ft, 'L' = 45ft. Returns 0 when unknown.
     */
    public int lengthFeet() {
        if (isoSizeType == null || isoSizeType.isEmpty()) {
            return 0;
        }
        return switch (isoSizeType.charAt(0)) {
            case '2' -> 20;
            case '4' -> 40;
            case 'L' -> 45;
            default -> 0;
        };
    }

    /** ISO 6346 type group letter, third character of the size-type code: G, R, U, P, T ... */
    public char typeGroup() {
        return isoSizeType != null && isoSizeType.length() >= 3 ? isoSizeType.charAt(2) : '?';
    }

    public boolean isReefer() {
        return typeGroup() == 'R';
    }

    /** Platforms and flat racks may legally share a cell when bundled or stacked. */
    public boolean isFlatOrPlatform() {
        return typeGroup() == 'P';
    }

    public record DangerousGood(String imdgClass, String unNumber, int position) {
    }
}
