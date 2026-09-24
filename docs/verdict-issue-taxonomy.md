# Verdict & Issue Taxonomy (T5)

This is the human-readable mirror of `com.tutran.callassistant.taxonomy` — the code is
the source of truth (`IssueCategoryRegistry`); this document exists so the taxonomy can
be reviewed without reading Java. Keep the two in sync.

## Verdict (PROJECT_SPEC.md §4.1)

| Verdict | Criteria checked by `RuleVerdictEngine` |
| --- | --- |
| `UNKNOWN` | No signaling data at all; or end-call logs missing for **both** legs; or the call appears connected (`OK_ACK_OK` observed) but no `BYE` was ever found (most likely truncated/incomplete data, not a real ongoing call) |
| `FAIL` | Signaling data exists and at least one leg's end-call log exists, but no `OK_ACK_OK` (confirmed) event was ever observed |
| `SUCCESS` | `OK_ACK_OK` observed and later followed by `BYE` — optionally flagged with a quality issue (see below) |

Quality flag (`SUCCESS` only) is raised when **either leg** breaches a threshold, checked
in this order (first match wins):

1. `audio.packetLostPercent > 5.0%` → `NETWORK_PACKET_LOSS`
2. `audio.jitter > 30.0ms` OR `transport.currentRttMs > 300.0ms` → `NETWORK_DELAY_JITTER`
3. `audio.audioMos < 3.5` (and neither of the above fired) → `UNKNOWN` (quality is clearly
   poor, but no specific network metric explains why)

These thresholds are a documented Sprint 1 baseline, not calibrated against ground
truth (none shipped with the sample data) — tightening them is explicit Sprint 3 work
(§7.1 T1).

## Issue Category (PROJECT_SPEC.md §4.2)

Applies to `FAIL` calls and to `SUCCESS` calls with a quality flag.

### NETWORK_PACKET_LOSS

- **Definition**: call connects and stays connected, but quality degrades from lost
  packets — not a connection-setup failure.
- **Symptoms**: high `audio.packetLostPercent`; low `audio.audioMos`.
- **Required evidence**: end-call log periodic stats.
- **Detection**: `audio.packetLostPercent > 5%` OR `audio.audioMos < 3.5` for either leg.
- **Known ambiguity**: co-occurs with jitter/RTT issues; without those fields we can't
  always tell which is primary.

### NETWORK_DELAY_JITTER

- **Definition**: quality degrades from delay/jitter rather than outright loss.
- **Symptoms**: high `audio.jitter`; high `transport.currentRttMs`.
- **Required evidence**: end-call log periodic stats.
- **Detection**: `audio.jitter > 30ms` OR `transport.currentRttMs > 300ms`.
- **Known ambiguity**: RTT/jitter fields aren't always present (device/version
  dependent); when absent, this can't be distinguished from `NETWORK_PACKET_LOSS` and
  the report must say so rather than guess.

### ICE_FAILURE

- **Definition**: media connection never established because ICE connectivity checks
  failed.
- **Symptoms**: WebRTC log's `onIceConnectionChange` reaches `FAILED`/`DISCONNECTED`;
  end-call state timeline never reaches `CONFIRMED`.
- **Required evidence**: WebRTC log `ICE_CONNECTION_STATE_CHANGE` event; end-call state
  timeline.
- **Detection**: an `ICE_CONNECTION_STATE_CHANGE` event whose message mentions
  `FAILED`/`DISCONNECTED`.
- **Known ambiguity**: hard to separate from `TURN_FAILURE` without per-candidate-pair
  detail — see below.

### TURN_FAILURE

- **Definition**: media connection could not be established (or degraded) because of a
  client-observed TURN allocation/relay error.
- **Not auto-detected in Sprint 1.** An early version pattern-matched free text for
  "turn" + "error"/"fail", but that flagged routine, self-recovering per-candidate TURN
  protocol responses as failures — confirmed on a real, clean `SUCCESS` call where it
  produced 26/16 false positives (the standard long-term-credential challenge and
  permission-negotiation retries that simply get abandoned in favor of a working
  candidate pair). Currently folds into `ICE_FAILURE` (via the reliable terminal ICE
  state) or `SIGNALING_FAILURE`. A real detector — likely needing per-candidate-pair
  state tracking, not single-line keyword matching — is Known Limitations work.
- **Out of scope regardless** (PROJECT_SPEC.md §9): diagnosing TURN server congestion
  or capacity, even once client-observed errors are detected reliably.

### SIGNALING_FAILURE

- **Definition**: call could not be established because of a signaling-layer problem:
  timeouts, excessive retransmits, or the callee session not being found.
- **Symptoms**: repeated INVITE/BYE retransmits; "No sessions found" in the end-call
  log; non-zero `callErrorCode` with the call ending before `CONFIRMED`.
- **Required evidence**: signaling log retries/timeouts; end-call log state
  timeline/log_detail.
- **Detection**: this is the Sprint 1 rule engine's **default FAIL category** whenever
  no `OK_ACK_OK` was observed and no ICE failure evidence was found — i.e. the call
  never got far enough for a media-layer problem to even be possible yet.
- **Known ambiguity**: a non-zero `callErrorCode` can reflect a legitimate user action
  (busy, declined) rather than a system failure — e.g. the real sample call
  `1B009D42-49CD-479E-B26C-3A2994AEB720` was rejected with `callErrorCode: 428`
  ("Người này hiện chưa thể nhận cuộc gọi") before ever sending an INVITE. Evidence
  must be read in context, not treated as proof of a technical fault.

### UNKNOWN

- Not enough evidence was available to attribute the issue to a specific category.

## A real data quirk that shaped this design

The end-call log's numeric `#HN` schema tag (see `sample.md`) is **not** a stable
identifier: the exact same logical schema (e.g. "call summary", or the ~150-field
periodic quality-stats record) appears under a *different* number in the caller vs.
callee log of the very same sample call
(`success/DE7DD314-F432-45CB-BCB4-AE9103CC0919`). `EndCallLogParser` and
`EndCallSchemaClassifier` therefore classify each header by the field names it
declares, never by its number — the same "identify by content, not by a name/number
that can vary" principle used for file-type detection.
