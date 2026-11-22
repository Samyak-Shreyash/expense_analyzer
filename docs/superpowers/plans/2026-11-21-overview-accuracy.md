# Overview Accuracy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Overview numbers trustworthy: detect own-account transfers and refunds, make Net and percentages consistent, categorise far more rows, and fix the listed UI bugs.

**Architecture:** Detection stays deterministic and runs after every import and statement delete (`TransferService.match`, new `ReversalService.match`), plus a new user-triggered rematch endpoint so existing rows benefit. Analytics fixes are confined to `SpendingAnalyticsService` and its DTOs. Classification improves through `MerchantNormalizer` rules, a new category migration and import-time keyword categories.

**Tech Stack:** Java 21, Spring Boot 4, JPA, Flyway, JUnit 5 + H2 (backend); React + TypeScript + Vitest (frontend).

**Spec:** `docs/superpowers/specs/2026-11-21-overview-accuracy-report.md`

## Global Constraints

- Gradle, not Maven. Backend verify: `cd backend && ./gradlew test --tests "<Class>"`; whole branch: `./gradlew clean build`.
- Frontend verify: `cd frontend && npx vitest run <file>` and `npx tsc --noEmit`.
- Never log PII, descriptions, names or amounts. Raw descriptions are never modified.
- Schema changes only through a new Flyway migration (next number: V27; check `db/migration/` first). Never edit an existing migration.
- Automatic marks are never manual: rows with `transferManual = true` are never touched by automatic matching.
- Edit only `backend/src/` and `frontend/src/`; ignore `backend/bin/`, `backend/build/`, `graphify-out/`.
- Percentages shown to users: one decimal place everywhere.

## Review Focus

1. A user with no `fullName` (null or blank): own-name detection must do nothing, never match everything. (Task 1 test)
2. Two unrelated same-amount debit/credit rows at different merchants within the window must not pair as a reversal. (Task 2 test)
3. A range with no data, or a single partial month, must not divide by zero in shares or prorated change. (Task 4 test)
4. Running rematch twice must be idempotent: no new pairs, no changed totals. (Task 8 test)
5. A user who deleted a default category: the V27 migration and seeding must not recreate other users' deleted categories beyond the four new names. (Task 6 note: migration inserts only the four new names, only where missing.)

## Out of scope (rulings)

- "transfers=only&transfer=true" and the unlabelled Transfers dropdown: current `TransactionsPage.tsx` uses only `transfer` and wraps the select in a `<label>`. Not reproducible in code; the audited deploy is likely stale. Re-check after deploy.
- "CRADJ → WF Salary" and "Denil/Shashank as Loan EMI": caused by stored user aliases/person categories, not visible without data. Task 2's ₹1 pairing removes CRADJ from totals; the rest needs the user's own edits.
- Notes inside person names ("Venkata Ramanamma See", "Sourav Ha"): not separable from surnames deterministically. Users can rename/merge people.
- Changing the existing Refund `counts_as_spending` for existing users: that's the user's setting. Only new seeds change (Task 6).

---

### Task 1: Own-account transfer detection

**Files:**
- Create: `backend/src/main/java/com/expenseanalyzer/transaction/service/OwnName.java`
- Modify: `backend/src/main/java/com/expenseanalyzer/transaction/service/TransferService.java`
- Test: `backend/src/test/java/com/expenseanalyzer/transaction/service/OwnNameTest.java`, `backend/src/test/java/com/expenseanalyzer/transaction/controller/TransferOwnNameTest.java`

**Interfaces:**
- Produces: `public final class OwnName { public static boolean mentions(String fullName, String text) }`.
- Produces: `TransferService.match(UUID userId)` keeps its signature; it now loads the user's `fullName` (inject `UserRepository`).

Rule for `mentions`: upper-case both and strip non-letters per token. F = the full name's letters joined (e.g. "SAMYAKSHREYASH"); first = its first token. For each start token i of `text`, C = the letters of tokens i..end joined. True when the common prefix of C and F has length ≥ max(6, first.length() + 2), or equals F.length(). False when `fullName` is null or blank, or when F has fewer than 6 letters.

- [ ] **Step 1: Write failing `OwnNameTest`** with fullName "Samyak Shreyash":
  - true: "SAMYAK SHREYASH UTIB", "SAMYAKSH HDFCB ANK", "UPI/123456789012/Samyaksh/x@okaxis", "P2M SAMYAK SHREYASH PAID V", "NEFT-EMI SAMYAK SHREYASH"
  - false: "SAMYAK JAIN", "ZOMATO", "SHREYASH KUMAR"; null fullName; "" fullName; fullName "Al Bo" with text "AL BO".
- [ ] **Step 2: Run** `./gradlew test --tests "com.expenseanalyzer.transaction.service.OwnNameTest"`; expect compile failure.
- [ ] **Step 3: Implement `OwnName.mentions`.**
- [ ] **Step 4: Run Step 2's command**; expect PASS.
- [ ] **Step 5: Write failing `TransferOwnNameTest`** (Spring + H2, follow `TransferPostgresTest`/`TransactionControllerTest` setup). The user's fullName is "Samyak Shreyash"; the user has two statements with different account numbers.
  - `ownNameRowsWithAPersonStillPair`: a DEBIT 40000.00 "SAMYAK SHREYASH UTIB" (with a personId set) on 3 Aug in account A and a CREDIT 40000.00 "SAMYAKSH HDFCB ANK" on 3 Aug in account B. After `match`, both have `transfer = true` and point at each other via `transferPairId`.
  - `unpairedOwnNameRowIsATransfer`: a CREDIT 4000.00 "Samyaksh" with no partner gets `transfer = true`, `transferPairId = null`, `transferManual = false`.
  - `manualUnmarkIsRespected`: an own-name row with `transferManual = true, transfer = false` stays untouched.
  - `otherPeopleStillExcluded`: a person row "RAHUL SHARMA" with a same-amount opposite partner is not paired (existing rule).
- [ ] **Step 6: Change `TransferService.match`.** An open row is eligible for pairing when it has no merchant/person (as now) OR `OwnName.mentions(fullName, rawDescription)`. After pairing, set `transfer = true` (unpaired) on every remaining open own-name row. Update the class Javadoc.
- [ ] **Step 7: Run** `./gradlew test --tests "*Transfer*"`; expect all PASS, including the existing transfer tests.
- [ ] **Step 8: Commit** `feat(transfer): detect transfers to the user's own name`.

### Task 2: Automatic refund/reversal pairing

**Files:**
- Create: `backend/src/main/java/com/expenseanalyzer/transaction/service/ReversalService.java`
- Modify: `backend/src/main/java/com/expenseanalyzer/processing/service/StatementProcessor.java:218`, `backend/src/main/java/com/expenseanalyzer/statement/service/StatementService.java:153`, `backend/src/main/java/com/expenseanalyzer/transaction/repository/TransactionRepository.java`
- Test: `backend/src/test/java/com/expenseanalyzer/transaction/controller/ReversalAutoTest.java`

**Interfaces:**
- Produces: `@Service public class ReversalService { @Transactional public void match(UUID userId) }`. It is called right after `transferService.match(userId)` at both call sites.

Pairing uses the same field effects as `TransactionService.setReversal`: both halves get `reversalPairId` set to each other, `reversalPending = false`, `transfer = false`, `transferManual` unchanged (automatic). `reversalChargePaise` stays null because amounts are equal. Candidates: `transfer = false`, `duplicate = false`, `reversalPairId == null`, `transferManual == false`. Debits in date order (ties: createdAt, then id) take the earliest eligible credit. A credit is eligible for a debit when:
- it has the same `amountCents` and currency;
- its date is on or after the debit's date and at most **30 days** later; and
- either (a) the amount is ≤ 100 paise and the gap is ≤ **3 days** (verification payments); or (b) they share a non-null `merchantId`; or (c) they share a token of ≥ 4 letters from `normalizedDescription` that is not in the stop set {BANK, PAYMENT, PAYMENTS, PAID, REFUND, REVERSAL, FROM, LIMITED, PVT, LTD, INDIA, SMALL, FINANCE} and is not part of the user's own name (`OwnName`).

- [ ] **Step 1: Write failing `ReversalAutoTest`:**
  - `zomatoRefundPairsWithOrder`: DEBIT 377.90 "UPI/P2M/123456789012/ZOMATO/order" 28 Oct; CREDIT 377.90 "UPI/P2P/223456789012/ZOMATO ME/refund" 30 Oct → paired; summary outflow for Oct excludes 377.90.
  - `rupeeVerificationPairs`: DEBIT 1.00 "DREAMPLUG" 5 Sep, CREDIT 1.00 "CRADJ" 6 Sep → paired.
  - `rupeeTooFarApart`: the same with the credit on 10 Sep → not paired.
  - `differentMerchantsSameAmountDoNotPair`: DEBIT 80.00 "SWIGGY" and CREDIT 80.00 "RAHUL SHARMA" one day apart → not paired.
  - `refundBeforePaymentDoesNotPair`: the credit dated the day before the debit → not paired.
  - `manualRowsUntouched`: a debit with `transferManual = true` is not paired.
  - `deleteStatementRematches`: after deleting the refund's statement, the debit's `reversalPairId` is null. Clear a pair's surviving half in `StatementService.delete` before rows are deleted, mirroring `transferService.releasePartners`; add `ReversalService.releasePartners(UUID userId, UUID statementId)` for it.
- [ ] **Step 2: Run** `./gradlew test --tests "*ReversalAutoTest"`; expect FAIL.
- [ ] **Step 3: Implement `ReversalService`** (plus a repository finder for candidates) and wire both call sites.
- [ ] **Step 4: Run** `./gradlew test --tests "*Reversal*" --tests "*StatementProcessorTest" --tests "*StatementPipelineE2ETest"`; expect PASS.
- [ ] **Step 5: Commit** `feat(reversal): pair refunds and verification payments automatically`.

### Task 3: Net and monthly cash flow match the headline cards

**Files:**
- Modify: `backend/src/main/java/com/expenseanalyzer/analytics/service/SpendingAnalyticsService.java:83-90,237-244,382-397`
- Test: `backend/src/test/java/com/expenseanalyzer/analytics/controller/AnalyticsNetTest.java`

**Interfaces:** API shapes unchanged. `Totals.netCents()` becomes `inflowCents - outflowCents`. `cashFlow` builds each month from `totals(userId, max(from, monthStart), min(to, monthEnd), resolution)`, so its inflow excludes loans and its outflow is spending, the same as the summary. `cashFlow` then needs `@Transactional` (not readOnly) because `forUser` may seed.

- [ ] **Step 1: Write failing tests.** Fixture: in Nov, a CREDIT 1000.00 salary, a CREDIT 500.00 categorised Loan, a DEBIT 300.00 uncategorised and a DEBIT 200.00 in Loan EMI.
  - `netIsInflowMinusOutflow`: summary inflow 1000.00, outflow 300.00, net 700.00, loanReceived 500.00, nonSpendingOutflow 200.00.
  - `cashFlowMatchesSummary`: the single Nov month has inflow 1000.00, outflow 300.00, net 700.00.
  - `cashFlowClipsToRange`: with the range 15 Nov–30 Nov and one more 100.00 debit on 10 Nov, Nov outflow stays 300.00.
- [ ] **Step 2: Run** `./gradlew test --tests "*AnalyticsNetTest"`; expect FAIL.
- [ ] **Step 3: Implement.** Update the class Javadoc's semantics paragraph. Remove the now-unused `monthly()` aggregation if `trends` no longer needs it. `trends` uses it for month keys, so keep a key-only helper.
- [ ] **Step 4: Run** `./gradlew test --tests "com.expenseanalyzer.analytics.*"`; existing assertions on net may change. Update them only where they encoded the old formula, and say so in the report.
- [ ] **Step 5: Commit** `fix(analytics): net equals money in minus money out`.

### Task 4: One spending total for shares; prorated month comparison

**Files:**
- Modify: `SpendingAnalyticsService.java` (`merchants`, `categories`, `trends`)
- Test: `backend/src/test/java/com/expenseanalyzer/analytics/controller/AnalyticsShareTest.java`

**Interfaces:** `MerchantBreakdown.totalSpend` and each `sharePercent` use the same total as `CategoryBreakdown.totalSpend`: the net spending over all spending groups plus loan interest. Extract `private BigDecimal spendingTotalCents(List<Group> groups, List<Transaction> interest)` and use it in both methods.
Trend change: for month m, covered(m) = the days of m inside [from, to]. `changePercent` compares `outflow(m)/covered(m)` with `outflow(m-1)/covered(m-1)`, computed as `changePercent(outflow(m) * covered(m-1), outflow(m-1) * covered(m))`. Previous zero → null, as now.

- [ ] **Step 1: Write failing tests:**
  - `merchantAndCategorySharesUseSameTotal`: Blinkit DEBIT 102.00 + unmatched person DEBIT 300.00 + Swiggy DEBIT 598.00. Blinkit's merchant share equals its category-path share; both breakdowns report `totalSpend` 1000.00.
  - `partialMonthIsProrated`: range 1 Oct–21 Nov, Oct spend 3100.00 (31 days → 100/day) and Nov spend 2100.00 (21 days → 100/day). Nov `changePercent` is 0.00.
  - `emptyRangeShares`: no rows → items empty, totalSpend 0.00, no exception.
- [ ] **Step 2: Run** `./gradlew test --tests "*AnalyticsShareTest"`; expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew test --tests "com.expenseanalyzer.analytics.*"`; expect PASS.
- [ ] **Step 5: Commit** `fix(analytics): consistent shares and prorated monthly change`.

### Task 5: Complete "Not counted as spending" breakdown

**Files:**
- Modify: `backend/src/main/java/com/expenseanalyzer/analytics/dto/AnalyticsDtos.java:58-60`, `SpendingAnalyticsService.nonSpending`
- Modify: the frontend type for the non-spending response, and its rendering in `frontend/src/features/dashboard/DashboardPage.tsx`
- Test: `backend/src/test/java/com/expenseanalyzer/analytics/controller/AnalyticsNonSpendingTest.java` (extend), `frontend/src/features/dashboard/DashboardPage.test.tsx` (extend)

**Interfaces:** `NonSpendingCategory(String category, BigDecimal spend, BigDecimal sharePercent, long transactionCount, List<PersonSpend> people, List<MerchantSpend> merchants, BigDecimal other)`. `merchants` lists merchant-tied groups with `sharePercent` null, sorted by spend descending then name. `other` is the remainder with neither a person nor a merchant. Invariant: the sum of people + the sum of merchants + other = spend.

- [ ] **Step 1: Write failing backend test** `breakdownSumsToCategoryTotal`. Loan EMI has a DEBIT 26813.00 tied to merchant "CRED", a DEBIT 36500.00 tied to person "Asha", and a DEBIT 5000.00 with neither. Expect merchants = [CRED 26813.00], people = [Asha 36500.00], other = 5000.00, spend = 68313.00.
- [ ] **Step 2: Run it**; expect FAIL. Implement. Run `./gradlew test --tests "com.expenseanalyzer.analytics.*"`; expect PASS.
- [ ] **Step 3: Frontend.** Render merchants under people with the same row markup. Render `other` > 0 as a row labelled "Other". Add a test asserting "CRED" and "Other" appear. Run `npx vitest run src/features/dashboard`; expect PASS.
- [ ] **Step 4: Commit** `fix(analytics): list merchants and other in non-spending breakdown`.

### Task 6: New categories, keyword categories, Refund default

**Files:**
- Create: `backend/src/main/resources/db/migration/V27__seed_rent_insurance_health_home_services.sql` (use `/create-migration`; confirm V27 is next)
- Modify: `CategoryService.java` (`ensureDefaults`), `MerchantNormalizer.java` (rules feed default names), `StatementProcessor.java` (`enrich` / after `markSelfAsTransfers`)
- Create: `backend/src/main/java/com/expenseanalyzer/category/service/KeywordCategory.java`
- Test: `backend/src/test/java/com/expenseanalyzer/category/service/KeywordCategoryTest.java`, extend `StatementProcessorTest`

**Interfaces:**
- `public final class KeywordCategory { public static Optional<String> of(String rawDescription) }` returns a category name for whole-token keywords (case-insensitive, split on non-letters): EMI → "Loan EMI"; RENT → "Rent"; INSURANCE, PREMIUM, POLICYBAZAAR, LIC → "Insurance"; PHARMACY, HOSPITAL, CLINIC, APOLLO, MEDPLUS, PHARMEASY → "Health". First match in this order.
- Migration: for every user, insert the categories "Rent", "Insurance", "Health" and "Home Services" (`counts_as_spending = TRUE`, `gen_random_uuid()`) where no category with that lower(name) exists.
- `ensureDefaults` seeds REFUND with `spends = false` for new users. Update the comment: refunds net by `netCategory`, so Refund's own flag only affects debits.
- Import: after enrichment, a fresh row with `categoryId == null`, no merchant, and a keyword hit whose category the user has gets `categoryId` set. Rows with a merchant keep merchant-based resolution.

- [ ] **Step 1: Write failing `KeywordCategoryTest`:** "NEFT-EMI SAMYAK SHREYASH" → Loan EMI; "UPI/P2A/123456789012/DUMMY NAME RENT" → Rent; "EMIRATES" → empty (whole token only); "PARENT" → empty; "APOLLO PHARMACY" → Health.
- [ ] **Step 2: Run**; FAIL. Implement. PASS.
- [ ] **Step 3: Extend `StatementProcessorTest`** with `emiAndRentRowsGetKeywordCategories`: import two rows → their categoryId is Loan EMI and Rent. Add the migration. Run `./gradlew test --tests "*StatementProcessorTest" --tests "*Category*"`; PASS. If Docker is available, also run `FlywaySchemaValidationTest`.
- [ ] **Step 4: Commit** `feat(category): rent/insurance/health categories and keyword categorisation`.

### Task 7: Merchant rules and names

**Files:**
- Modify: `backend/src/main/java/com/expenseanalyzer/merchant/service/MerchantNormalizer.java`, `PersonNameParser.java`
- Test: `MerchantNormalizerTest.java`, `PersonNameParserTest.java` (extend)

**Interfaces:** `RULES_VERSION = 3`. New rules, with multi-token aliases ordered before their prefixes:
`URBANCLAP`/`URBAN COMPANY`/`URBANCOMPANY` → "Urban Company", Home Services; `KFC` → "KFC", Food & Dining; `RAMESHWARAM`/`THE RAMES` → "The Rameshwaram Cafe", Food & Dining; `SMARTQ`/`THESMARTQ` → "SmartQ", Food & Dining; `JUBILANT`/`DOMINO` → "Domino's", Food & Dining; `INDIAN RAILWAYS` → "IRCTC", Travel; `POLICYBAZAAR` → "Policybazaar", Insurance; `EKART` → "Ekart", Shopping.
A bank suffix also counts when the next token is a ≥2-letter prefix of "PAYMENTS" and is the last token ("AIRTEL PA").
Second pass: if no rule matches, retry on the raw upper-cased tokens with trailing digits stripped ("THESMARTQ1" → "THESMARTQ"). This pass is for matching only; `normalizedDescription` is unchanged, so payee keys stay stable.
`PersonNameParser.parse`: a segment that starts with the placeholder "Dummy Name" (followed by any remark) is treated as the placeholder. Trailing tokens with no letters ("Bhavya .") are dropped. `displayName` of a bare account number returns "A/c ending " + its last 4 digits.

- [ ] **Step 1: Write failing tests:** "P2M URBANCLAP AIRTEL PA" → Urban Company; "P2M AIRTEL PA" → `canonicalName` null (the bank, not Airtel); "AIRTEL RECHARGE" → Airtel; "UPI/P2M/123456789012/THESMARTQ1" → SmartQ; "THE RAMES" → The Rameshwaram Cafe; "POLICYBAZAAR INSURANC" → Policybazaar; existing tests unchanged. Person: "UPI/P2A/631770008709/DUMMY NAME RENT" parses to "631770008709", and `displayName` of that is "A/c ending 8709"; "UPI/P2P/123456789012/BHAVYA ." → "Bhavya".
- [ ] **Step 2: Run** `./gradlew test --tests "*MerchantNormalizerTest" --tests "*PersonNameParserTest"`; FAIL. Implement. PASS.
- [ ] **Step 3: Run** `./gradlew test --tests "*GoldenStatementAccuracyTest" --tests "*PayeeKeyTest"`; PASS (payee keys unchanged).
- [ ] **Step 4: Commit** `feat(merchant): more merchant rules, bank-suffix and name fixes`.

### Task 8: Rematch existing transactions

**Files:**
- Modify: `TransactionController.java` (add `POST /api/transactions/rematch`), `TransactionService.java`
- Modify: `frontend/src/features/statements/StatementsPage.tsx` (button + copy), the frontend API client used by that page
- Test: `backend/src/test/java/com/expenseanalyzer/transaction/controller/RematchTest.java`, `StatementsPage.test.tsx`

**Interfaces:** `POST /api/transactions/rematch` → 204, caller-scoped. It re-runs enrichment (merchant rules, keyword category) only on the caller's rows with `merchantId == null && personId == null && categoryId == null`. Then it runs `transferService.match(userId)` and `reversalService.match(userId)`. Extract the enrichment from `StatementProcessor.enrich` into a reusable bean method rather than duplicating it.

- [ ] **Step 1: Write failing `RematchTest`:** an imported unmatched "KFC" row gains the KFC merchant after rematch; a row with a user-set category is untouched; a second rematch changes nothing (compare summary before/after); unauthenticated → 401.
- [ ] **Step 2: Run**; FAIL. Implement. Run `./gradlew test --tests "*RematchTest" --tests "*StatementProcessorTest"`; PASS.
- [ ] **Step 3: Frontend.** Add a "Re-check all transactions" button on Statements that calls the endpoint and shows "Done. Overview is up to date." Replace the copy at `StatementsPage.tsx:136` with: "Each row is imported once, even if your files overlap. Money moved between your own accounts is detected and shown as a transfer." Test the button and the new copy; `npx vitest run src/features/statements`; PASS.
- [ ] **Step 4: Commit** `feat(transactions): rematch existing transactions`.

### Task 9: Frontend fixes

**Files:**
- Modify: `frontend/src/lib/format.ts`, `frontend/src/features/dashboard/DashboardPage.tsx` (and `charts.tsx` if it prints %), `frontend/src/features/transactions/TransactionsPage.tsx`, `frontend/src/features/categories/CategoriesPage.tsx:305-310`, `frontend/src/components/AppLayout.module.css:40-56`
- Test: the matching `*.test.tsx` files, plus `frontend/src/lib/format.test.ts`

**Interfaces:** `export function percent(n: number): string` formats with exactly one decimal (`28` → "28.0%", `22.256` → "22.3%"). `changeLabel` and every percentage rendered on the dashboard use it.

- [ ] **Step 1: Write failing tests:**
  - `percent(28)` = "28.0%", `percent(22.26)` = "22.3%"; `changeLabel(-51.96)` = "52.0% less than the previous period".
  - Transactions: min 1000 + max 100, then Apply → the alert "Min amount can't be more than max amount." appears and the URL is unchanged.
  - Categories: the Loan switch (non-spending) has the accessible description text "Loan does not count as spending"; Food's says "Food counts as spending". Keep the switches' accessible names matching the existing tests.
- [ ] **Step 2: Run** `npx vitest run`; FAIL on the new tests. Implement. For the nav, inside the existing narrow media query make `.nav` `flex-wrap: wrap` so no link is clipped at 450px.
- [ ] **Step 3: Run** `npx vitest run && npx tsc --noEmit`; PASS. Run the `frontend-check` skill if available.
- [ ] **Step 4: Commit** `fix(frontend): percent format, amount range check, a11y text, nav wrap`.

### Task 10: Whole-branch verification

- [ ] `cd backend && ./gradlew clean build` → BUILD SUCCESSFUL.
- [ ] `cd frontend && npx vitest run && npx tsc --noEmit` → all pass.
