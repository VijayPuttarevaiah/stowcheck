package dev.vijay.stowcheck.edifact;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw EDIFACT text into segments.
 *
 * <p>Honours the optional UNA service string advice, which can redefine the separators,
 * and the release character ('?' by default) that escapes a separator inside a value,
 * e.g. {@code FTX+AAA+++DRUMS ?+ PALLETS'}. Line breaks between segments are ignored,
 * since many senders wrap one segment per line.
 */
public final class EdifactTokenizer {

    private EdifactTokenizer() {
    }

    public static List<Segment> tokenize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new EdifactSyntaxException("Message is empty");
        }

        String text = raw.strip();
        char component = ':';
        char element = '+';
        char release = '?';
        char terminator = '\'';

        if (text.startsWith("UNA")) {
            if (text.length() < 9) {
                throw new EdifactSyntaxException("UNA service string advice is truncated");
            }
            component = text.charAt(3);
            element = text.charAt(4);
            release = text.charAt(6);
            terminator = text.charAt(8);
            text = text.substring(9);
        }

        List<Segment> segments = new ArrayList<>();
        List<List<String>> elements = new ArrayList<>();
        List<String> components = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (escaped) {
                current.append(ch);
                escaped = false;
            } else if (ch == release) {
                escaped = true;
            } else if (ch == component) {
                components.add(current.toString());
                current.setLength(0);
            } else if (ch == element) {
                components.add(current.toString());
                elements.add(components);
                components = new ArrayList<>();
                current.setLength(0);
            } else if (ch == terminator) {
                components.add(current.toString());
                elements.add(components);
                segments.add(toSegment(elements, segments.size() + 1));
                elements = new ArrayList<>();
                components = new ArrayList<>();
                current.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                // wrapped lines between segments carry no data
            } else {
                current.append(ch);
            }
        }

        if (!current.toString().isBlank() || !elements.isEmpty()) {
            throw new EdifactSyntaxException(
                    "Last segment is missing its " + terminator + " terminator");
        }
        return segments;
    }

    private static Segment toSegment(List<List<String>> elements, int position) {
        String tag = elements.get(0).get(0).strip();
        if (!tag.matches("[A-Z]{3}")) {
            throw new EdifactSyntaxException(
                    "Segment " + position + " has an invalid tag '" + tag + "'");
        }
        return new Segment(tag, List.copyOf(elements.subList(1, elements.size())), position);
    }
}
