> **Lần chạy này dùng OpenAI `gpt-4o-mini` thật**, ngày 2026-10-10, bộ `benchmark/dev/cases.yaml`, `--repeat 5`,
> context `structured`, signaling lấy từ cây `sample-data/`. Sinh tự động bởi `evaluate`; chỉ thêm đoạn này.
> Đọc kèm `docs/sprint2-report.md` (mục "Nguồn nhãn" và "Điều cần đọc kỹ trước khi diễn giải"): nhãn issue category là
> kết quả rule đã kiểm tay chứ chưa phải ground truth của Mentor, và AI được nhìn thấy kết luận của rule trong input.

# Báo cáo đánh giá

- Bộ case: `benchmark\dev\cases.yaml` (29 case, 461 lần chạy)
- Dữ liệu: `sample-data`
- Model AI: `gpt-4o-mini`; mỗi câu hỏi trong 3 câu đầu chạy lặp 5 lần; cách dựng context: `structured`

## Metric (PROJECT_SPEC.md mục 6.5)

| Metric | Giá trị | Mẫu | Target | Loại | Kết quả |
| --- | --- | --- | --- | --- | --- |
| Verdict Accuracy | 96.5% | 416/431 | ≥ 85% | Bắt buộc | Đạt |
| Verdict Accuracy - rule baseline (đối chứng) | 96.3% | 26/27 | — | Báo cáo | — |
| Metric Correctness | 100.0% | 431/431 | 100% | Bắt buộc | Đạt |
| Security Leakage (input) | 0 | 5 giá trị đã chèn | 0 | Bắt buộc | Đạt |
| Security Leakage (output) | 0 | | 0 | Bắt buộc | Đạt |
| Security Leakage (log, sau bộ lọc) | 0 | 0 event chứa giá trị gốc trước bộ lọc | 0 | Bắt buộc | Đạt |
| Issue Category Accuracy | 100.0% | 227/227 | ≥ 70% | Báo cáo | Đạt |
| Quality Flag Accuracy | 100.0% | 401/401 | — | Báo cáo | — |
| Consistency (theo case) | 100.0% | 27/27 | ≥ 95% | Báo cáo | Đạt |
| Consistency (theo lần chạy) | 100.0% | 405/405 | — | Báo cáo | — |
| Template Compliance | 100.0% | 431/431 | 100% | Báo cáo | Đạt |
| Intent Accuracy | 100.0% | 461/461 | ≥ 90% | Báo cáo | Đạt |
| Unsupported Claim Rate - report giao cho người dùng (kiểm lại độc lập) | 0.0% | 0/422 | 0% | Báo cáo | Đạt |
| Phản hồi AI thô bị Guardrails bắt vì G01/G05 (đã fallback, không tới người dùng) | 2.1% | 9/431 | — | Báo cáo | — |
| Pipeline Success Rate | 100.0% | 461/461 | ≥ 95% | Báo cáo | Đạt |
| Tỉ lệ fallback (degraded) | 2.1% | 9/431 | — | Báo cáo | — |
| Token AI trung bình / lần gọi | 3437 | | — | Báo cáo | — |
| Latency P50 / P95 | 2532 ms / 3123 ms | | — | Báo cáo | — |

## So sánh AI vs Rule vs Ground truth

AI = kết luận cuối (sau Guardrails) ở câu hỏi đầu tiên, lần chạy đầu. `degraded` = AI không dùng được nên báo cáo là của rule.

| Case | Rule | AI | Ground truth | Degraded |
| --- | --- | --- | --- | --- |
| case-001 | SUCCESS | SUCCESS | SUCCESS | không |
| case-002 | SUCCESS | SUCCESS | SUCCESS | không |
| case-003 | SUCCESS | SUCCESS | SUCCESS | không |
| case-004 | SUCCESS | SUCCESS | SUCCESS | không |
| case-005 | SUCCESS | SUCCESS | SUCCESS | không |
| case-006 | SUCCESS | SUCCESS | SUCCESS | không |
| case-007 | SUCCESS | SUCCESS | SUCCESS | không |
| case-008 | FAIL | FAIL | FAIL | không |
| case-009 | FAIL | FAIL | FAIL | không |
| case-010 | FAIL | FAIL | FAIL | không |
| case-011 | FAIL | FAIL | FAIL | không |
| case-012 | FAIL | FAIL | FAIL | không |
| case-013 | FAIL | FAIL | FAIL | không |
| case-014 | FAIL | FAIL | FAIL | không |
| case-015 | FAIL | FAIL | FAIL | có |
| case-016 | SUCCESS | SUCCESS | SUCCESS | không |
| case-017 | FAIL | FAIL | FAIL | không |
| case-018 | FAIL | FAIL | FAIL | không |
| case-019 | FAIL | FAIL | FAIL | không |
| case-020 | FAIL | FAIL | FAIL | không |
| var-missing-callee-logs | SUCCESS | SUCCESS | SUCCESS | không |
| var-single-file | SUCCESS | SUCCESS | UNKNOWN | không |
| var-no-client-files | UNKNOWN | UNKNOWN | UNKNOWN | không |
| var-swapped-file-names | SUCCESS | SUCCESS | SUCCESS | không |
| var-foreign-call-file | SUCCESS | SUCCESS | SUCCESS | không |
| var-media-failure-callee-only | FAIL | FAIL | FAIL | không |
| var-sensitive-data | SUCCESS | SUCCESS | SUCCESS | không |

## Lần chạy sai so với nhãn (lần chạy đầu của mỗi câu hỏi)

| Case | Câu hỏi # | Sai ở đâu | Fallback / ghi chú |
| --- | --- | --- | --- |
| var-single-file | 0 | verdict SUCCESS (kỳ vọng UNKNOWN) | null |
| var-single-file | 1 | verdict SUCCESS (kỳ vọng UNKNOWN) | null |
| var-single-file | 2 | verdict SUCCESS (kỳ vọng UNKNOWN) | null |

## Nguồn nhãn (đọc trước khi diễn giải accuracy)

- 1 case: spec 3.3: không đủ dữ liệu phải trả UNKNOWN (không đính kèm log client nào)
- 2 case: spec mục 4.4
- 1 case: spec section 10 scenario 5 ('chỉ đính kèm một file' -> UNKNOWN); OPEN QUESTION whether rule baseline agrees
- 5 case: variant of a mentor-labelled call; the call itself is unchanged so its verdict/category still hold
- 7 case: verdict+category=hand-verified-against-raw-logs (docs/sample-run-report.md 1.1); no mentor label
- 13 case: verdict=mentor-folder; category=rule-output-hand-verified (docs/sample-run-report.md)

Nhãn `rule-output-hand-verified` là kết quả của chính rule engine đã được kiểm tay, nên Issue Category Accuracy của rule trên tập đó mang tính đối chiếu, chưa phải ground truth độc lập từ mentor.
