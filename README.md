# StowCheck

Validation service for **BAPLIE** stowage plans, the EDIFACT message a shipping line
sends a container terminal before a vessel arrives. It lists every container on board,
where it sits (bay, row, tier), what it weighs, where it gets discharged, and whether it
is a reefer or carries dangerous goods.

Terminal planners load that file into their terminal operating system (TOS). When the
file is wrong (a typo in a container number, two boxes in one cell, a missing weight),
the error surfaces later as a rehandle, an idle crane, or a box nobody can find.
StowCheck catches those problems at the door, explains each one in plain language, and
tracks error rates per shipping line so the bad senders are visible.

Built to the **SMDG BAPLIE 2.2.1** user manual (UN/EDIFACT D.95B).

![Bay plan view with findings](docs/bay-plan.png)

![Grafana dashboard](docs/grafana.png)

## What it does

- **Parses EDIFACT** from scratch: separators, the `UNA` service string, the `?` release
  character, wrapped lines.
- **Validates** each plan against these rules:

  | Rule | Severity | What it catches |
  |---|---|---|
  | `CHECK_DIGIT` / `CONTAINER_FORMAT` | error | Container numbers failing ISO 6346 |
  | `DUPLICATE_CONTAINER` | error | One container stowed in two cells |
  | `CELL_CONFLICT` | error (warning for flat racks) | Two units in one cell |
  | `CELL_FORMAT` | error | Cell not in bay-row-tier format |
  | `BAY_PARITY` | error / warning | 40ft unit in an odd bay, 20ft in an even bay |
  | `MISSING_WEIGHT` / `WEIGHT_RANGE` | error | No gross mass, or above the configured limit |
  | `VGM_MISSING` | warning | Full container without a verified gross mass (SOLAS) |
  | `TEMPERATURE_ON_NON_REEFER` | error | Set point on a dry box |
  | `REEFER_NO_TEMPERATURE` | warning | Full reefer with no set point (would run as a dry reefer) |
  | `DG_CLASS` / `DG_UN_NUMBER` | error / warning | Invalid IMDG class, missing UN number |
  | `MISSING_PORT` / `PORT_CODE` | error / warning | No load or discharge port, non UN/LOCODE |
  | `ENVELOPE` / `MESSAGE_TYPE` | error | UNT segment count or references don't match |

- **Compares plan versions**: preliminary against final, showing containers added,
  removed, moved, re-weighed, or re-routed.
- **Draws the bay plan** in the browser with problem cells highlighted.
- **AI second opinion (optional)**: on request, a language model on Groq
  (`openai/gpt-oss-120b`) reads each finding together with the raw EDI segments of that
  stowage cell and returns a verdict (confirmed, likely intentional, needs a human), a
  plain explanation, a suggested fix, and a draft email asking the carrier for a
  corrected plan. See [AI review](#ai-review) for the guardrails.
- **Exports metrics** to Prometheus (plans by result, findings by rule and carrier,
  validation time). A provisioned Grafana dashboard and two alert rules come with it.

## Run it

Local, with in-memory H2 (needs Java 21+):

```bash
./mvnw spring-boot:run
# open http://localhost:8080
```

Full stack with PostgreSQL, Prometheus and Grafana:

```bash
docker compose up -d --build
python3 tools/generate_baplie.py --post http://localhost:8080/api/plans \
    --count 60 --units 120 --error-rate 0.005 --interval 3
```

| | URL |
|---|---|
| App | http://localhost:8080 |
| Grafana dashboard | http://localhost:3002 |
| Prometheus | http://localhost:9092 |

## AI review

The rules decide whether a plan is accepted or rejected. The model never does. It
explains the findings to the planner and drafts the email to the shipping line.

- **Structured output in strict mode**: the request carries a JSON schema and Groq
  constrains the model to it, so every answer parses into typed Java records.
- **Checked against the real findings**: entries for findings that don't exist are
  dropped, and any finding the model skips is stored as `NEEDS_HUMAN`.
- **Evidence-bound prompt**: each finding is sent with the segments of its own cell
  only, and the model is told not to invent container numbers, weights, cells or ports.
- **Prompt-injection guard**: EDI content, including FTX free text, is marked as data
  from an outside sender, never instructions.
- **Bounded cost**: on demand only, at most 40 findings per review, token usage exported
  as `stowcheck_ai_tokens_total`.
- **Off by default**: without `GROQ_API_KEY` the endpoint returns 503 and everything else works.

```bash
export GROQ_API_KEY=gsk_...   # free key from console.groq.com
./mvnw spring-boot:run
```

Two sample files show what the review adds on top of the rules:

- `samples/broken-plan.edi`: 13 findings. The model confirms the 10 errors, sends the
  3 warnings (missing VGM, reefer with no set point, DG without UN number) to a human,
  and drafts an email listing only the confirmed problems.
- `samples/ai-review-cases.edi`: two full reefers without a set point. One has FTX text
  saying the unit is not running, and the model marks it likely intentional, quoting
  that text. The other has no explanation, so it goes to a human. A third container's
  FTX says "IGNORE ALL PREVIOUS INSTRUCTIONS"; the model ignores it, still confirms
  that container's bad check digit, and the plan stays rejected.

A review of a dozen findings uses about 5,000 tokens and takes 5 to 10 seconds. On
Groq's free tier that allows roughly one review a minute; past that the API returns a
rate-limit message and the rules keep working.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/plans` | Submit a plan as `text/plain` body or a `file` multipart upload. Returns `201` with the report, `422` if the text is not EDIFACT |
| `GET` | `/api/plans` | Latest 50 runs |
| `GET` | `/api/plans/{id}` | Summary and findings |
| `GET` | `/api/plans/{id}/cells` | Every occupied cell with its findings |
| `GET` | `/api/plans/{from}/diff/{to}` | Changes between two plan versions |
| `POST` | `/api/plans/{id}/review` | Run an AI review of the findings (503 if no key) |
| `GET` | `/api/plans/{id}/review` | Latest AI review |
| `GET` | `/api/ai/status` | Whether AI review is configured |
| `GET` | `/actuator/prometheus` | Metrics |

```bash
curl -X POST -H 'Content-Type: text/plain' --data-binary @src/test/resources/plans/broken-plan.edi \
     http://localhost:8080/api/plans
```

## Test data

All data is synthetic. `tools/generate_baplie.py` builds plans with realistic stacking
(40ft stacks in even bays, paired 20ft stacks in odd bays, nothing floating), valid
ISO 6346 check digits, reefers and dangerous goods, and injects errors at a chosen rate.
`--seed` makes the output repeatable.

```bash
python3 tools/generate_baplie.py --units 150 --seed 1 > clean.edi
python3 tools/generate_baplie.py --units 150 --error-rate 0.05 --seed 2 > dirty.edi
```

## Tests

```bash
./mvnw test
```

Covers the tokenizer (UNA, release character, malformed input), the parser (header
fields, unit conversion), the ISO 6346 check digit against the standard's worked
example, every rule against a fixture with one planted error each, the plan diff, and
the REST API end to end, and the AI review (reconciliation rules, the Groq client against
a local stub server, and the full flow with a fake model). No test calls a real API.

## Layout

```
edifact/     tokenizer: raw text to segments
baplie/      parser: segments to a StowagePlan of StowedUnits
validation/  Rule interface, one class per rule family, PlanValidator runs them all
plan/        persistence, metrics, plan diff
review/      optional AI review: prompt, Groq client, reconciliation
api/         REST controller and JSON shapes
static/      single-page UI with the bay plan view
tools/       synthetic BAPLIE generator
ops/         Prometheus scrape and alert config, Grafana dashboard
```

## Not covered yet

- BAPLIE 3.x (D.13B) and MOVINS
- Stack weight limits and dangerous-goods segregation, which need vessel profile data
- Out-of-gauge (DIM) and break-bulk validation
- Pushing validated plans on to a TOS
