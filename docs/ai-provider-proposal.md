# AI Provider Proposal (T10)

**Status: draft, pending Mentor approval (due end of Sprint 1, PROJECT_SPEC.md §1.4).**
This compares two options that run from a personal machine, per §5.1 T10's requirement.
Nothing here is used yet — Sprint 1 has no AI in the pipeline at all (§3.2/§3.3); this
proposal only sets up what Sprint 2's AI Analysis Engine will call.

## What the AI actually needs to do (from §3.2/§6.1 T5)

Input: intent/focus (from Request Parser) + timeline + evidence + computed metrics +
taxonomy — never raw log lines (see `docs/sensitive-data-inventory.md`'s last section).
Output: a structured object matching `report-schema-v1.json`'s AI-filled fields
(`verdict` suggestion, `qualityFlag`, `issueCategory`, `summary`, `evidenceIds`,
`analysis`, `suggestions`) that Guardrails then checks against the rule baseline. So the
two properties that matter most for provider choice are: **reliable structured/JSON
output** (Guardrails needs to parse it deterministically) and **acceptable latency** for
an interactive web UI (Sprint 2 §6.5 measures P50/P95 end-to-end).

## Option A: Cloud API (Anthropic Claude, e.g. Haiku or Sonnet)

| Criterion | Assessment |
| --- | --- |
| Data sent after sanitize | Only the sanitized timeline/evidence/metrics JSON (§6.2) leaves the machine, over TLS to Anthropic's API. No raw log lines, no `SECRET`-classified fields ever leave the sanitizer. |
| Cost | Pay-per-token. A single call's sanitized context (timeline + evidence + metrics, not raw logs) is small — low hundreds to low thousands of tokens — so per-analysis cost is a small fraction of a cent to a few cents depending on model tier. Needs a real API key and budget; free-tier/trial credits may cover Sprint 2-3 development volume. |
| Latency | Typically ~1-3s for a small-to-medium structured-output request on a fast model tier; consistent regardless of the developer's own machine's CPU/GPU. |
| Structured output support | Strong: supports constrained/tool-based structured output and JSON mode, which maps directly onto validating against `report-schema-v1.json` in Guardrails. |
| Other | Requires an internet connection and an API key (cost/secrets management); nothing runs "outside the team's systems" since it's a public API call, which is allowed per §1.3 (only *team-internal* systems are off-limits) but should still be called out explicitly to the Mentor as an external network dependency. |

## Option B: Local model via Ollama (e.g. Llama 3.1 8B Instruct or Qwen2.5 7B Instruct)

| Criterion | Assessment |
| --- | --- |
| Data sent after sanitize | Nothing leaves the machine at all — strongest possible answer to "AI chỉ nhận minimum necessary context" and to input/output leakage risk, since there's no network hop to begin with. |
| Cost | Free to run (open-weight model, no per-token billing); only cost is local compute/electricity and disk space for the model weights (a few GB). |
| Latency | Highly dependent on the developer's own hardware. On a CPU-only laptop, expect noticeably higher latency than the cloud option (seconds to tens of seconds per request) and lower throughput for the repeated-run consistency benchmark (§6.5 "chạy 5 lần × 3 cách hỏi"); a machine with a capable GPU narrows this gap substantially. Needs to be measured on the actual dev machine before committing. |
| Structured output support | Weaker and more model-dependent: smaller open models are less reliable at strictly following a JSON schema without extra scaffolding (e.g. grammar-constrained decoding via Ollama's `format: json` option, or a stricter prompt + retry loop in Guardrails). More Sprint 2 engineering effort to get Guardrails' "invalid → reject/fallback" path exercised reliably. |
| Other | Fully offline-capable once the model is pulled, which is convenient for demoing without network dependency; but repeated consistency benchmarking (§6.5) will take materially longer wall-clock time in Sprint 2-3 if latency is high. |

## Recommendation (draft — confirm against your own machine's specs before submitting)

Lead with **Option A (cloud API)** for Sprint 2 development speed and structured-output
reliability, since Guardrails' correctness depends on the AI actually returning
parseable, schema-valid JSON consistently — that's the harder problem to solve with a
small local model on unknown hardware. Keep **Option B (local model)** documented as the
fallback/offline story and revisit it in the "So sánh cách dựng context" stretch task
(§6.1 T11) if cost or connectivity becomes a real constraint.

**Before finalizing:** confirm your actual API budget/key access for Option A, and if
you want to keep Option B live, do a quick manual latency check with your own GPU/CPU
before writing that number into this table as fact rather than an estimate.
