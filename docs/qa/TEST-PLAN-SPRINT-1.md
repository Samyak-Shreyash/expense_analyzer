# Test Plan — QA Sprint 1 (2 weeks)

Goal: close the gaps that let regressions merge silently (strategy roadmap, weeks 1–2, plus the highest-risk item from weeks 3–4).

| # | Task | Risk | Effort | Done when |
|---|---|---|---|---|
| 1 | Add `frontend` job to `.github/workflows/ci.yaml` (`npm ci && npm run ci && npm test`) | R6 | 0.5 d | PR shows 3 green jobs |
| 2 | Fix or remove `main.yml` placeholder action | — | 0.1 d | Workflow passes or is gone |
| 3 | Add `javascript-typescript` to CodeQL | R7 | 0.2 d | CodeQL runs both languages |
| 4 | JaCoCo + Vitest/Jest coverage; record baseline in `docs/qa/` | — | 1 d | Reports uploaded as CI artifacts |
| 5 | Disable OTLP export in test profile | — | 0.1 d | No `4318` warnings in test logs |
| 6 | Replace 3 `Thread.sleep` with Awaitility / fixed `Clock` | flake | 0.5 d | `grep Thread.sleep src/test` returns nothing |
| 7 | Golden statement corpus: 3 anonymized files per format (CSV, Excel, PDF, GPay) + expected JSON + accuracy-reporting test | R3 | 3 d | Suite reports accuracy per format; CSV ≥98% |
| 8 | Port `AnalyticsControllerTest` + `DuplicateTest` to Testcontainers Postgres with Flyway on | R1, R4, R5 | 2 d | Both pass on Postgres in CI |

**Total effort:** ~7.5 days. **Deferred to Sprint 2:** Playwright journeys, contract check, axe.

## Regression scenarios to add in item 8

- Monthly totals across a month boundary in IST vs UTC
- Transfer between own accounts excluded from spend
- Reversal nets to zero
- Same file uploaded twice gives 0 new rows
- Overlapping statements (same txn in two files) give 1 row
- Paise rounding: sum of 3 × ₹33.33
