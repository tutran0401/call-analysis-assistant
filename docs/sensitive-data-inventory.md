# Sensitive Data Inventory & Classification Policy (T9)

Expands PROJECT_SPEC.md §5.2's starter table with fields actually found while building
the Sprint 1 parsers, by scanning the real sample data in `fail/`, `success/`,
`for_test/`. No sanitizer is implemented yet (Sprint 2, §6.2) — this is the inventory
that sanitizer will enforce. Nothing in Sprint 1 is sent to an AI provider, so there is
no leakage surface yet, but the parsers already extract these fields into
`CanonicalEvent.attributes()`, so the policy below tells Sprint 2 what to mask/drop
before anything reaches an LLM.

Classification levels: `PUBLIC`, `INTERNAL`, `SENSITIVE`, `SECRET` (per §5.2).

## From the mentor-provided starter table

| Field | Classification | Policy | Where observed |
| --- | --- | --- | --- |
| Packet loss, RTT, jitter, MOS, call duration | Internal | Allow | End-call log periodic stats / call summary |
| ISP / ASN / country | Internal | Allow | Signaling events (`isp`, `asn`, `countryCode`) |
| Phone number, email | Sensitive | Mask | Not observed in the sample data itself, but must still be masked if present in future data |
| Client IP address | Sensitive | Mask | WebRTC/end-call ICE candidate fields (`transport.localCandidate.ip`, `transport.remoteCandidate.ip`, raw `candidate:` SDP lines), device network descriptors (`pdp_ip0:100.81.44.x/32:Cellular` — partially pre-masked by the client itself in some fields, but **not** in the ICE candidate IPs, which are full addresses) |
| User ID (incl. internal), Device ID | Sensitive | Pseudonymize | `appUserId`, `callUserId`, `partnerAppUserId`, `partnerCallUserId` (end-call log, signaling events); `deviceId` (embedded in end-call log `log_detail` JSON blobs) |
| Session ID, csid | Sensitive | Pseudonymize if needed to correlate | `sessionId`, `csid`, `requestId` (end-call log + signaling events) |
| JWT, Authorization Header, API Key, TURN credential | Secret | Drop | No auth headers observed in these client-side logs; TURN credentials are present (see below) |
| Private infra detail (pod name, internal host, TURN server IP) | Internal/Sensitive | Minimize | TURN server URLs in `iceServers.urls` (embedded config JSON), e.g. `turn:14.238.152.70:3478` |

## Additional fields found while building the Sprint 1 parsers

| Field | Classification | Policy | Where observed |
| --- | --- | --- | --- |
| SDP ICE ufrag/pwd (`a=ice-ufrag`, `a=ice-pwd`) | Secret | Drop | End-call log `SIGNALING_CMD` rows' embedded SDP offer/answer JSON |
| DTLS fingerprint (`a=fingerprint:sha-256 ...`) | Secret | Drop | Same embedded SDP blobs |
| `turnSessionInfo` / `turnLoggingId` | Sensitive | Pseudonymize | Embedded `rtcConfig` JSON in end-call log `log_detail` rows. Note: the client **already partially masks** `turnSessionInfo` itself in the sample data (e.g. `"DE7D****************************0919"`) — the sanitizer must not assume every field needing masking is still in the clear by the time it reaches us. |
| Vietnamese user-facing error text (`callErrorMsg`, `callError`) | Internal | Allow | End-call log `recv_cmd` rows, e.g. `"Người này hiện chưa thể nhận cuộc gọi"`. Free text but describes call state, not personal data — kept Internal unless a future sample embeds identifying info in it. |
| `callErrorCode` | Internal | Allow | Same rows. A numeric status code, not sensitive by itself. |
| Raw SDP body (`sdpOffer`/`sdpAnswer`) as a whole | Sensitive | Minimize | Contains ICE ufrag/pwd, DTLS fingerprint and candidate IPs together — treat the whole blob as sensitive-until-scrubbed rather than trying to regex out only the known-bad substrings. |
| Log message free text (`msg`, `message` in `LOG_MESSAGE`/`SIGNAL` rows) | Internal | Minimize | Can embed IDs or config values inline (e.g. `deviceId=...` appears inside a `log_detail` message string) — treat as needing the same scrubbing as structured fields, not just the structured fields themselves. |

## Classification summary by data type

| Classification | Meaning here | Examples |
| --- | --- | --- |
| `PUBLIC` | Safe to show/log anywhere | (none identified yet in this dataset — everything client-side carries at least device/user context) |
| `INTERNAL` | Safe within the team/system, not sent raw to an external AI provider without review | Metrics, ISP/ASN/country, error codes/messages |
| `SENSITIVE` | Must be masked or pseudonymized before leaving the system | User/device/session IDs, client IPs, TURN server addresses |
| `SECRET` | Must be dropped entirely, never logged or sent anywhere | ICE ufrag/pwd, DTLS fingerprints, any TURN long-term credential |

## Implication for Sprint 2's Input Sanitizer

The AI Analysis Engine's input (per §3.3, "AI chỉ nhận minimum necessary context") should
be built from `CallTimeline`/`CallMetrics`/`Evidence` — which already summarize events
into a small attribute set — rather than from raw `rawLine`/`rawEvent` strings, since
those raw strings are exactly where the embedded SDP/JSON blobs (and therefore the
`SECRET`-classified fields above) live. The sanitizer still needs to scrub the
`SENSITIVE`/`INTERNAL` fields that do get forwarded (IDs, IPs), but keeping raw log
lines out of the AI context entirely removes the biggest leakage surface by
construction.
