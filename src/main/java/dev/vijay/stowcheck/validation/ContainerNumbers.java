package dev.vijay.stowcheck.validation;

/**
 * ISO 6346 container numbers: 3-letter owner code, equipment category (U, J or Z),
 * 6-digit serial, 1 check digit. Example: CSQU3054383.
 */
public final class ContainerNumbers {

    private static final String FORMAT = "[A-Z]{3}[UJZ]\\d{7}";

    private ContainerNumbers() {
    }

    public static boolean hasValidFormat(String id) {
        return id != null && id.matches(FORMAT);
    }

    /**
     * Computes the check digit from the first 10 characters.
     *
     * <p>Letters take values from 10 upward, skipping 11, 22 and 33 (multiples of 11),
     * so A=10, B=12 ... K=21, L=23 ... Each character's value is multiplied by 2^position,
     * the products are summed, and the check digit is the sum mod 11, with 10 written as 0.
     */
    public static int checkDigit(String id) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += charValue(id.charAt(i)) * (1 << i);
        }
        return sum % 11 % 10;
    }

    public static boolean hasValidCheckDigit(String id) {
        return hasValidFormat(id) && checkDigit(id) == id.charAt(10) - '0';
    }

    private static int charValue(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        int index = c - 'A';
        // skip 11, 22 and 33: B moves past 11, L past 22, V past 33
        int skipped = (index + 9) / 10;
        return 10 + index + skipped;
    }
}
