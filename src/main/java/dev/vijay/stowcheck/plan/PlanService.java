package dev.vijay.stowcheck.plan;

import dev.vijay.stowcheck.baplie.BaplieParser;
import dev.vijay.stowcheck.baplie.StowagePlan;
import dev.vijay.stowcheck.edifact.EdifactSyntaxException;
import dev.vijay.stowcheck.validation.Issue;
import dev.vijay.stowcheck.validation.PlanValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Parse, validate, store, and count. Metrics are tagged by rule and carrier so the
 * Grafana dashboard can show which partner sends which kind of bad data.
 */
@Service
public class PlanService {

    private final PlanValidator validator;
    private final ValidationRunRepository runs;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final Timer validationTimer;

    public PlanService(PlanValidator validator, ValidationRunRepository runs,
                       MeterRegistry metrics, Clock clock) {
        this.validator = validator;
        this.runs = runs;
        this.metrics = metrics;
        this.clock = clock;
        this.validationTimer = Timer.builder("stowcheck.validation.duration")
                .description("Time to parse and validate one BAPLIE message")
                .register(metrics);
    }

    @Transactional
    public ValidationRun submit(String rawMessage) {
        Timer.Sample sample = Timer.start(metrics);
        StowagePlan plan;
        try {
            plan = BaplieParser.parse(rawMessage);
        } catch (EdifactSyntaxException e) {
            count("unreadable", "unknown");
            throw e;
        }

        List<Issue> issues = validator.validate(plan);
        sample.stop(validationTimer);

        ValidationRun run = new ValidationRun(rawMessage, Instant.now(clock));
        run.recordHeader(plan.sender(), plan.carrier(), plan.vesselName(), plan.voyage(),
                plan.messageRef(), plan.units().size());
        run.recordIssues(issues);
        runs.save(run);

        String carrier = plan.carrier() == null || plan.carrier().isBlank() ? "unknown" : plan.carrier();
        count(run.getStatus().name().toLowerCase(), carrier);
        metrics.counter("stowcheck.units.checked", "carrier", carrier).increment(plan.units().size());
        for (Issue issue : issues) {
            Counter.builder("stowcheck.issues")
                    .tag("rule", issue.rule())
                    .tag("severity", issue.severity().name().toLowerCase())
                    .tag("carrier", carrier)
                    .register(metrics)
                    .increment();
        }
        return run;
    }

    @Transactional(readOnly = true)
    public ValidationRun get(long id) {
        ValidationRun run = runs.findById(id).orElseThrow(() -> new RunNotFoundException(id));
        run.getIssues().size(); // load the lazy collection inside the transaction
        return run;
    }

    @Transactional(readOnly = true)
    public List<ValidationRun> recent() {
        return runs.findTop50ByOrderByReceivedAtDesc();
    }

    private void count(String result, String carrier) {
        metrics.counter("stowcheck.plans", "result", result, "carrier", carrier).increment();
    }
}
