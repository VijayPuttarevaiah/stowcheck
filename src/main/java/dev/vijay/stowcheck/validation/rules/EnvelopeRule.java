package dev.vijay.stowcheck.validation.rules;

import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Envelope integrity. UNT must carry the real segment count and repeat the UNH
 * reference; UNZ must repeat the UNB reference. A wrong count is the usual sign of a
 * file that was truncated in transit or hand-edited.
 */
@Component
public class EnvelopeRule implements Rule {

    @Override
    public List<Issue> check(StowagePlan plan) {
        List<Issue> issues = new ArrayList<>();

        if (!"BAPLIE".equals(plan.messageType())) {
            issues.add(header("MESSAGE_TYPE", "Message type is " + plan.messageType() + ", not BAPLIE"));
        }
        if (plan.declaredSegmentCount() == null) {
            issues.add(header("ENVELOPE", "UNT trailer is missing, so the message may be truncated"));
        } else if (plan.declaredSegmentCount() != plan.actualSegmentCount()) {
            issues.add(header("ENVELOPE", "UNT declares " + plan.declaredSegmentCount()
                    + " segments but the message has " + plan.actualSegmentCount()));
        }
        if (plan.declaredSegmentCount() != null
                && !Objects.equals(plan.messageRef(), plan.messageTrailerRef())) {
            issues.add(header("ENVELOPE", "UNT reference " + plan.messageTrailerRef()
                    + " does not match UNH reference " + plan.messageRef()));
        }
        if (plan.interchangeRef() != null && plan.interchangeTrailerRef() != null
                && !plan.interchangeRef().equals(plan.interchangeTrailerRef())) {
            issues.add(header("ENVELOPE", "UNZ reference " + plan.interchangeTrailerRef()
                    + " does not match UNB reference " + plan.interchangeRef()));
        }
        return issues;
    }

    private static Issue header(String rule, String message) {
        return new Issue(rule, Issue.Severity.ERROR, null, null, 0, message);
    }
}
