package dev.vijay.stowcheck.edifact;

import java.util.List;

/**
 * One EDIFACT segment, e.g. {@code LOC+147+0120082::5'}.
 *
 * <p>Elements are separated by '+', components inside an element by ':'.
 * {@code elements.get(0)} is the first element after the tag.
 *
 * @param position 1-based position of the segment in the interchange, used in error reports
 */
public record Segment(String tag, List<List<String>> elements, int position) {

    /** Component {@code c} of element {@code e} (both 0-based), or "" when absent. */
    public String value(int e, int c) {
        if (e >= elements.size()) {
            return "";
        }
        List<String> components = elements.get(e);
        return c < components.size() ? components.get(c) : "";
    }

    /** First component of element {@code e}. */
    public String value(int e) {
        return value(e, 0);
    }
}
