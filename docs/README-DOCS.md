# Expense Analyzer Documentation Index

This documentation set is the connected product/engineering baseline for Expense Analyzer.

- `PRD.md` — product vision, goals, principles, scope, and release strategy.
- `MVP-REQUIREMENTS.md` — detailed first-release functional requirements and acceptance criteria.
- `USER-JOURNEYS.md` — end-to-end user flows and failure/edge cases.
- `SUCCESS-METRICS.md` — product, trust, performance, processing, and AI metrics.
- `NON-GOALS.md` — explicitly deferred functionality.
- `HLD.md` — high-level architecture and evolution path.
- `DOMAIN-MODEL.md` — entities, relationships, invariants, and financial semantics.
- `API-CONTRACT.md` — versioned API surface and conventions.
- `EVENT-MODEL.md` — asynchronous event envelope, events, delivery, and versioning.
- `BACKLOG.md` — implementation roadmap and dependencies.

## Dependency Direction

`PRD.md`
→ `MVP-REQUIREMENTS.md` / `NON-GOALS.md` / `SUCCESS-METRICS.md` / `USER-JOURNEYS.md`
→ `DOMAIN-MODEL.md` / `HLD.md`
→ `API-CONTRACT.md` / `EVENT-MODEL.md`
→ `BACKLOG.md`

When a decision changes the product contract, update the upstream document first and then propagate the change downstream.
