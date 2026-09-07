# Success Metrics

**Document ID:** PROD-003  
**Version:** 0.1  
**Depends on:** PRD.md

## 1. Product Metrics

| Metric | MVP Target | Definition |
|---|---:|---|
| Activation | ≥60% | New users who upload one statement in first session |
| Statement processing success | ≥95% | Supported statements completing successfully |
| Time to value | <5 min typical | Upload to first useful analytics/insight |
| Insight engagement | ≥50% | Activated users viewing at least one insight |
| Supported CSV extraction accuracy | ≥98% | Correct extraction of supported fields |
| Common categorization accuracy | ≥85% | Correct category on common transactions |
| Merchant normalization coverage | ≥90% | Covered common merchant patterns |
| Duplicate prevention | 100% | Identical uploads do not duplicate transactions |
| Financial calculation correctness | 100% | Deterministic totals/percentages match expected results |

## 2. Trust and Security Metrics
- 0 confirmed cross-user data leaks.
- 0 credential/token leakage in logs.
- 100% protected resources enforce ownership.
- 100% production uploads pass configured validation controls.

## 3. Technical Performance Targets
- Typical API response: <500 ms.
- Dashboard load: <2 s for normal users.
- Upload initiation: <2 s.
- Transaction filtering: <1 s for normal datasets.
- Typical statement processing: <5 min.
- API error rate: <1%.

## 4. Processing Metrics
Track:
- documents uploaded
- processing success/failure rate
- processing duration p50/p95
- rows extracted
- rows rejected
- duplicate transactions prevented
- queue depth
- retry count
- dead-letter count

## 5. AI Metrics (MVP+)
- tool-call success rate
- grounded-answer rate
- unsupported-question rate
- hallucination/validation failure rate
- AI latency p50/p95
- LLM cost per query
- fallback-to-analytics rate

## 6. Observability
Use correlation/request IDs to connect API → job → event → worker → analytics operations. Metrics and traces should not contain full statement contents or unnecessary sensitive financial information.

## 7. Measurement Rules
Every metric must have:
- explicit numerator/denominator
- event/source definition
- time window
- owner
- dashboard/query definition
- alert threshold where operationally relevant
