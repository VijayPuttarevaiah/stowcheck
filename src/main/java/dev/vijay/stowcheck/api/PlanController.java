package dev.vijay.stowcheck.api;

import dev.vijay.stowcheck.baplie.BaplieParser;
import dev.vijay.stowcheck.plan.PlanDiff;
import dev.vijay.stowcheck.plan.PlanService;
import dev.vijay.stowcheck.plan.ValidationRun;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    /** Submit a BAPLIE message as the raw request body (how a partner system would push it). */
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Dtos.Report> submitText(@RequestBody String body) {
        return created(plans.submit(body));
    }

    /** Submit a BAPLIE file from the upload page. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dtos.Report> submitFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        return created(plans.submit(new String(file.getBytes(), StandardCharsets.ISO_8859_1)));
    }

    @GetMapping
    public List<Dtos.Summary> recent() {
        return plans.recent().stream().map(Dtos.Summary::from).toList();
    }

    @GetMapping("/{id}")
    public Dtos.Report get(@PathVariable long id) {
        return Dtos.Report.from(plans.get(id));
    }

    /** Every occupied cell with its findings, for drawing the bay plan. */
    @GetMapping("/{id}/cells")
    public List<Dtos.Cell> cells(@PathVariable long id) {
        ValidationRun run = plans.get(id);
        return Dtos.Cell.from(BaplieParser.parse(run.getRawMessage()), run.getIssues());
    }

    /** Containers added, removed, moved, re-weighed or re-routed between two plan versions. */
    @GetMapping("/{fromId}/diff/{toId}")
    public PlanDiff diff(@PathVariable long fromId, @PathVariable long toId) {
        ValidationRun from = plans.get(fromId);
        ValidationRun to = plans.get(toId);
        return PlanDiff.between(fromId, BaplieParser.parse(from.getRawMessage()),
                toId, BaplieParser.parse(to.getRawMessage()));
    }

    private static ResponseEntity<Dtos.Report> created(ValidationRun run) {
        return ResponseEntity.created(URI.create("/api/plans/" + run.getId()))
                .body(Dtos.Report.from(run));
    }
}
