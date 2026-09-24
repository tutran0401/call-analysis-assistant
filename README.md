# AI Call Analysis Assistant — Sprint 1

Rule-based baseline for the OJT AI 20K call analysis assistant (see `docs/PROJECT_SPEC.md`).
Sprint 1 scope: **for a single call's logs, compute metrics, build a timeline, and
produce a rule-based verdict with evidence — no AI yet.**

## Stack

Java 17, Spring Boot 3.3.5 (CLI only, no web server), Maven. Elasticsearch 8.15 via
Docker Compose for signaling logs.

## Setup

Prerequisites: JDK 17+, Maven, Docker Desktop.

```bash
# 1. Start Elasticsearch (+ Kibana) locally
docker compose up -d

# 2. Build
mvn clean package

# 3. Import the mentor-provided sample signaling logs into Elasticsearch
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar import success fail for_test
```

Elasticsearch needs ~1-2 GB of heap (configured in `docker-compose.yml`); Kibana is
available at http://localhost:5601 once both containers are up.

## Running the demo

```bash
# One call, using the ES-backed signaling fetch
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze success/DE7DD314-F432-45CB-BCB4-AE9103CC0919

# One call, reading signaling.json directly instead of querying ES
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar analyze fail/1B009D42-49CD-479E-B26C-3A2994AEB720 --from-file

# Every call folder under one or more roots (the Sprint 1 "demo >= 5 calls" deliverable)
java -jar target/call-analysis-assistant-0.1.0-SNAPSHOT.jar demo success fail for_test
```

Each call prints a report in the fixed layout from `PROJECT_SPEC.md` §4.5, followed by
any parser warnings (missing files, malformed rows, etc.) that don't belong in the
formal report but are useful when debugging.

## Tests

```bash
mvn test
```

All parser, timeline, metrics, evidence/rule-verdict and report-schema tests run
against the real sample data in `fail/`, `success/` and `for_test/` — several
expected values (durations, retransmit counts, MOS/packet-loss/RTT/jitter) are
hand-computed directly from those files, not just asserted against the code's own
output.

## Project layout

```
src/main/java/com/tutran/callassistant/
  domain/     Canonical Event Model (T2)
  parser/     Log Normalizer: file-type detection + the 3 source parsers (T3)
  timeline/   Call Timeline Builder (T4)
  taxonomy/   Verdict & Issue Taxonomy (T5)
  metrics/    Call Metrics Calculator (T6)
  evidence/   Evidence Engine + Rule Verdict (T7)
  report/     Report Schema v1 + renderer (T8)
  es/         Elasticsearch import + query (T1)
  cli/        Demo entrypoint
docs/
  architecture-diagram-v1.md
  verdict-issue-taxonomy.md
  sensitive-data-inventory.md
  ai-provider-proposal.md
```

## Known Limitations (Sprint 1)

- **TURN-specific failure detection is not reliable yet.** An early version
  pattern-matched free text for "turn"/"ice" + "error"/"fail", but that flagged
  routine, self-recovering per-candidate TURN protocol responses as failures even on
  a clean SUCCESS call (see the `evidence`/`parser` package Javadoc and commit
  history for the concrete false-positive counts). `RuleVerdictEngine` currently
  folds TURN-layer failures into `ICE_FAILURE` (via the engine's own terminal
  `onIceConnectionChange` state) or `SIGNALING_FAILURE`. A dedicated TURN detector is
  deferred to a later sprint.
- **"Nếu kịp" metrics (§4.3) are not implemented**: PAIR_PING gap proxy, internal API
  latency at INIT_CALL, server WARN/ERROR counts, ISP/ASN/country context. Sprint 1
  prioritized the Core metrics.
- **Confidence level is a provisional placeholder.** `ConfidenceLevel` derives
  HIGH/MEDIUM/LOW from data completeness and category definiteness only; the full
  design comparing AI vs. rule agreement is Sprint 3 T3.
- **No sanitizer yet.** Sensitive/secret fields are inventoried in
  `docs/sensitive-data-inventory.md`, but masking/pseudonymization/dropping is a
  Sprint 2 deliverable (nothing is sent to an AI provider in Sprint 1, so there is
  no leakage surface yet).
- **AI Provider Proposal** (`docs/ai-provider-proposal.md`) is drafted but not yet
  approved by the Mentor.
