# Implementation Backlog

**Document ID:** PM-001  
**Version:** 0.1

## Priority
- **P0:** release-critical
- **P1:** required for MVP
- **P2:** important after MVP
- **P3:** future

## Status
BACKLOG / READY / IN_PROGRESS / BLOCKED / IN_REVIEW / DONE

## Sprint 0 — Product and Architecture

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| PROD-001 | P0 | Define MVP requirements | PRD | DONE |
| ARCH-001 | P0 | Establish ADR structure | PRD | READY |
| ARCH-002 | P0 | Define modular boundaries | ARCH-001 | READY |
| ARCH-003 | P0 | Define domain model | PRD | READY |
| API-001 | P0 | Define API contract | DOMAIN-MODEL | READY |
| EVT-001 | P0 | Define event envelope and schemas | DOMAIN-MODEL | READY |
| PM-001 | P1 | Establish backlog workflow | PRD | READY |

## Sprint 1 — Foundation

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| FOUND-001 | P0 | Create Spring Boot project | ARCH-002 | READY |
| FOUND-002 | P0 | Configure PostgreSQL | FOUND-001 | READY |
| FOUND-003 | P0 | Add Flyway migrations | FOUND-002 | READY |
| FOUND-004 | P0 | Create local Docker Compose stack | FOUND-002 | READY |
| FOUND-005 | P0 | Add application configuration/secrets strategy | FOUND-001 | READY |
| TEST-001 | P1 | Establish unit/integration test infrastructure | FOUND-001 | READY |

## Sprint 2 — Identity and Ownership

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| AUTH-001 | P0 | Implement User domain | FOUND-003 | READY |
| AUTH-002 | P0 | Implement authentication | AUTH-001 | READY |
| AUTH-003 | P0 | Implement resource ownership authorization | AUTH-002 | READY |
| AUTH-004 | P1 | Add auth integration tests | AUTH-002 | READY |
| AUDIT-001 | P1 | Add security audit logging | AUTH-003 | READY |

## Sprint 3 — Statement Ingestion

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| ACCT-001 | P0 | Implement Account domain/API | AUTH-003 | READY |
| STMT-001 | P0 | Implement Statement domain | ACCT-001 | READY |
| STMT-002 | P0 | Implement object-storage abstraction | STMT-001 | READY |
| STMT-003 | P0 | Implement statement upload API | STMT-002 | READY |
| STMT-004 | P0 | Implement file validation/limits | STMT-003 | READY |
| STMT-005 | P0 | Implement checksum/deduplication | STMT-004 | READY |
| STMT-006 | P0 | Implement processing state machine | STMT-001 | READY |

## Sprint 4 — CSV Processing

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| DOC-001 | P0 | Define parser interface | STMT-006 | READY |
| DOC-002 | P0 | Implement CSV parser | DOC-001 | READY |
| TXN-001 | P0 | Implement Transaction domain | DOC-002 | READY |
| TXN-002 | P0 | Validate extracted transactions | TXN-001 | READY |
| TXN-003 | P0 | Implement transaction fingerprinting | TXN-001 | READY |
| TXN-004 | P0 | Persist extracted transactions | TXN-002 | READY |
| ASYNC-001 | P0 | Implement job/event abstraction | STMT-006 | READY |
| ASYNC-002 | P0 | Implement document worker | ASYNC-001, DOC-002 | READY |
| ASYNC-003 | P0 | Connect upload to async processing | ASYNC-002 | READY |

## Sprint 5 — Transaction Intelligence

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| MER-001 | P1 | Implement Merchant domain | TXN-004 | READY |
| MER-002 | P1 | Implement deterministic merchant normalization | MER-001 | READY |
| CAT-001 | P1 | Implement Category domain/seed data | TXN-004 | READY |
| CAT-002 | P1 | Implement categorization rules | CAT-001, MER-002 | READY |
| REC-001 | P1 | Implement recurring-expense detection | MER-002, CAT-002 | READY |
| ANOM-001 | P1 | Implement explainable anomaly rules | CAT-002 | READY |
| PDF-001 | P1 | Define supported PDF fixtures/layouts | DOC-001 | READY |
| PDF-002 | P1 | Implement structured/text PDF parser | PDF-001 | READY |

## Sprint 6 — Analytics

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| ANAL-001 | P0 | Implement spending summary | TXN-004 | READY |
| ANAL-002 | P0 | Implement category analytics | CAT-002 | READY |
| ANAL-003 | P0 | Implement monthly/period trends | ANAL-001 | READY |
| ANAL-004 | P0 | Implement merchant analytics | MER-002 | READY |
| ANAL-005 | P1 | Implement cash-flow analytics | ANAL-001 | READY |
| ANAL-006 | P1 | Implement period comparison | ANAL-001 | READY |
| ANAL-007 | P1 | Expose recurring analytics | REC-001 | READY |
| ANAL-008 | P1 | Expose anomaly analytics | ANOM-001 | READY |

## Sprint 7 — Insights and API

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| INS-001 | P1 | Implement Insight domain | ANAL-001 | READY |
| INS-002 | P1 | Generate deterministic insight candidates | INS-001, ANAL-006 | READY |
| API-002 | P0 | Implement transaction query/filter API | TXN-004 | READY |
| API-003 | P0 | Implement statement status API | STMT-006 | READY |
| API-004 | P1 | Implement analytics APIs | ANAL-001 | READY |
| API-005 | P1 | Implement insight APIs | INS-001 | READY |

## Sprint 8 — Quality and Production Hardening

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| TEST-002 | P0 | End-to-end CSV upload pipeline test | ASYNC-003, ANAL-001 | READY |
| TEST-003 | P0 | Duplicate upload/idempotency tests | STMT-005, TXN-003 | READY |
| TEST-004 | P0 | Cross-user authorization tests | AUTH-003 | READY |
| TEST-005 | P1 | PDF fixture regression suite | PDF-002 | READY |
| PERF-001 | P1 | Concurrent upload performance test | TEST-002 | READY |
| RES-001 | P1 | Worker/DB/object-storage failure tests | ASYNC-003 | READY |
| OBS-001 | P1 | Structured logging/correlation IDs | FOUND-001 | READY |
| OBS-002 | P1 | Metrics and tracing | OBS-001 | READY |
| SEC-001 | P0 | File-security/resource-exhaustion review | STMT-004 | READY |
| SEC-002 | P0 | Secrets and sensitive-log review | FOUND-005 | READY |

## Sprint 9 — AI (MVP+)

| ID | Priority | Task | Depends On | Status |
|---|---|---|---|---|
| AI-001 | P1 | Define supported financial intents | ANAL-004 | READY |
| AI-002 | P1 | Define controlled analysis tools | AI-001 | READY |
| AI-003 | P1 | Implement intent routing | AI-002 | READY |
| AI-004 | P1 | Implement LLM explanation layer | AI-003 | READY |
| AI-005 | P1 | Add response grounding/validation | AI-004 | READY |
| AI-006 | P1 | Add AI fallback to deterministic analytics | AI-005 | READY |

## Future P2/P3 Work
- Direct bank integrations.
- Account Aggregator.
- OCR/scanned PDFs.
- Advanced ML classification.
- Portfolio/investment integrations.
- OpenSearch/analytics database when justified.
- Service extraction from modular monolith.
- Kubernetes/multi-region.
- Advanced notifications.
- Mobile-native applications.

## Definition of Done
A task is DONE only when implementation, tests, validation, error handling, relevant observability, documentation/API/event updates, security review, and local verification are complete. Significant features also require performance, concurrency, failure, and backward-compatibility checks where applicable.

## Working Rule
Always choose the highest-value unblocked P0/P1 task. Do not begin AI, Kubernetes, or premature microservice extraction while the core ingestion → transaction → analytics vertical slice remains incomplete.
