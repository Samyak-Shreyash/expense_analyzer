# Plan: Overview data fixes and dashboard redesign

**Date:** 21 Nov 2026
**Status:** Proposed, not started
**Mockup:** https://claude.ai/artifact/2Ee5gG8ZnhNBcaQ6NUp2tA (Overview redesign, built from real account figures)

## Why

A review of the live app (1,717 transactions, 1 Sept – 21 Nov 2026) found that the Overview page looks plain, and
that the numbers on it are unreliable:

- 95% of spending (₹11.13L of ₹11.66L) is "Uncategorized"; 97% of merchant spend is "Unmatched".
- Only 2 of 1,717 rows are flagged as transfers. Moves between the user's own accounts (e.g. 3 Aug −₹40,000 /
  +₹40,000 "SAMYAKSH HDFCB ANK") count as both money in and money out.
- Refunds and failed-payment reversals are not paired (Zomato ₹377.90 on 30 Oct, Urbanclap, AU Small Finance ±₹80).
- Some merchant matches are wrong ("P2M URBANCLAP AIRTEL PA" → Airtel / Utilities; "CRADJ" → WF Salary).
- Headline Net (−₹16,821.95) does not equal Money in − Money out (−₹58,106.95).

Redesigning first would put a prettier face on wrong numbers, so the data fixes come first.

## Root causes found in the code

| Symptom | Cause | Where |
|---|---|---|
| Own-account transfers not paired | `match()` skips every row that has a `personId` or `merchantId`. "Samyaksh" is parsed as a person, so both halves are excluded. Rows from statements without an `accountNumber` are also never paired. | `transaction/service/TransferService.java` |
| "URBANCLAP AIRTEL PA" → Airtel | `namesABank()` only recognises the full `PAYMENTS BANK` / `BANK` after a brand. Truncated forms (`PA`, `PAYMEN`, `AIRP`) slip through. | `merchant/service/MerchantNormalizer.java` |
| "CRADJ" → WF Salary | With no UPI payee, `PayeeKey.of()` falls back to the normalized description. A generic bank code like `CRADJ` becomes an alias key, so one rename relabels every row with that code. | `merchant/service/PayeeKey.java` |
| "DOMINOSPIZZA", "URBANCLAP", "KFC" etc. unmatched | Rules are whole-token and the rule list has 20 brands. | `MerchantNormalizer.RULES` |
| Refunds shown as people ("Zomato Me", "Urbanclap") | `PersonNameParser` treats every P2A as a person, even when the segment is a merchant brand plus `REFUND`. | `merchant/service/PersonNameParser.java` |
| Reversals not paired | Pairing exists but is manual only (`setReversal`, `ReversalRequest`). | `transaction/service/TransactionService.java` |
| Net ≠ in − out | Deliberate: `netCents()` is cash in minus cash out and adds loans received back in. The UI labels don't say so. | `analytics/service/SpendingAnalyticsService.java` |
| Same merchant, two percentages | Top merchants excludes person groups, so its total differs from "Where it went". | `SpendingAnalyticsService` (`share(...)` callers) |

## Decisions needed before starting

1. **What "Net" means.** (a) Keep cash-flow net (includes loans and EMIs) and relabel it "Cash flow", or
   (b) show Money in − Money out and move loans to their own card. The mockup uses (b).
2. **How to recognise "me" for transfers.** Recommended: a "This is me" flag on a Person, set once by the user
   (e.g. on "Samyaksh"), so pairing stays deterministic. The alternative is matching the user's profile name, which
   is fuzzier.
3. **Theme.** Dark only (as in the mockup), or dark and light with a toggle. The current tokens are light-only
   (`color-scheme: light` in `frontend/src/index.css`).
4. **Budgets.** Out of scope here. The reference image's "Remaining budget" card needs a budgets feature first.

## Phase 1 — Transfers (backend)

1. Add `is_self BOOLEAN NOT NULL DEFAULT FALSE` to the `people` table (new Flyway migration, V27).
2. In `TransferService.match()`, allow rows whose person is marked as self; keep excluding other people and
   merchants.
3. Pair rows where one side has no account number but the other does, when the descriptions name the user's
   own account (`SELF TRANSFER`, or a self-person). Keep the 3-day window and same-amount rule.
4. Expose the flag in `PersonController` and add a "This is me" toggle on the People tab (Categories page).
5. Re-run matching for the user after the flag changes.

**Tests:** `TransferServiceTest` cases for self-person pairs, a stranger with the same amount (not paired), and
pairs outside the window. Check on the real account: the 3 Aug ±₹40,000 pair and the recurring +₹4,000 credits
should appear under "Moved between your accounts".

## Phase 2 — Refunds and reversals (backend)

1. Auto-pair a reversal: a CREDIT of the same amount as a DEBIT to the same payee key, within 3 days, where the
   credit is no earlier than the debit. Reuse the existing reversal pairing so manual unpairing still works.
2. A P2A whose segment is a known merchant brand followed by `REFUND` resolves to that merchant, not a person,
   and nets against its spending (Zomato, Urbanclap).
3. ₹1 account-verification pairs (±₹1 same day, same payee) pair as reversals under the same rule. Unpaired single
   ₹1 credits stay as they are; don't guess.

**Tests:** paired and unpaired cases from the real data (Zomato 30 Oct, AU Small Finance ±₹80 on 16 Nov, Dreamplug
±₹1 on 18 Nov), plus a partial refund (must not auto-pair).

## Phase 3 — Merchant matching (backend)

1. `namesABank()`: treat truncated payment-bank suffixes after a brand (`PA`, `PAY`, `PAYMEN`, `PAYMENT`,
   `AIRP`) as the bank. Add test: "P2M URBANCLAP AIRTEL PA" must not match Airtel.
2. `PayeeKey`: don't create a description-fallback key from generic bank codes (`CRADJ`, `DRADJ` and similar).
   Renaming such a row should name only that row. Clean up the existing bad alias for the account.
3. Extend `RULES` with unambiguous brands seen in the data: Urban Company (`URBANCLAP`), Domino's
   (`DOMINOSPIZZA`), KFC, IRCTC/Indian Railways, Ekart, Policybazaar, CRED, Google Play (`GOOGLE INDIA DIGITAL`).
   No guesses for local shops.
4. Bump `RULES_VERSION` to 3. Check how existing rows get re-normalised when the version changes; if nothing
   does it, add a one-off re-normalise for the user's rows.
5. Add missing default categories the data needs: Rent, Insurance, Health. Keyword `RENT` in a P2A remark suggests
   Rent only after the user confirms once (keep "nothing is inferred from free text").

**Tests:** extend `MerchantNormalizer` and `PayeeKey` tests; every new rule gets a positive and a "names a bank"
negative case.

## Phase 4 — Overview numbers (backend + frontend)

1. Apply decision 1 to Net and make the cash-flow table use the same definition as the headline cards (today the
   November row includes the ₹1.66L loan in money in).
2. Use one denominator (Money out) for the shares in "Where it went" and "Top merchants".
3. "Not counted as spending": list merchants as well as people, so the Cred EMI (₹58,215 principal) shows.
4. Month-on-month change for a month in progress compares the same number of days (1–21 Nov vs 1–21 Oct), or is
   labelled "so far".
5. Default the Refund category to "does not count as spending".

**Tests:** `AnalyticsController` tests asserting headline in − out = Net under the chosen definition, and that
monthly rows sum to the headline figures.

## Phase 5 — Small UI fixes (frontend)

- Transactions: reject Min amount > Max amount with an inline message.
- Transactions: "Only transfers" writes `transfers=only&transfer=true`; keep one parameter.
- Transactions: give the Transfers select a `<label>` like the other filters.
- Categories: the screen-reader text reads "No, Loan counts as spending"; say "does not count".
- Top navigation clips "Categories" below ~450px; superseded by the sidebar in Phase 6.

**Tests:** extend `TransactionsPage.test.tsx` and `CategoriesPage.test.tsx`.

## Phase 6 — Dashboard redesign (frontend)

Follow the mockup. Keep the existing semantic-token approach: new colours go into `index.css` as tokens, never raw
hex in components.

1. **Theme tokens:** add the dark palette (ground, card, card border, money-in green, money-out orange, accent teal,
   category colours) per decision 3. Category colours differ in lightness, not only hue.
2. **Layout:** sidebar navigation in `components/AppLayout.tsx` that stacks to a top bar on phones.
3. **Summary cards:** Money in, Money out, Net, Loans & EMIs, each with the period comparison.
4. **Cash flow chart:** grouped monthly bars (money in vs spent) in `features/dashboard/charts.tsx`, replacing
   `LedgerChart`.
5. **Spending by category:** a donut of categorised spending plus a callout linking to uncategorised transactions
   while the uncategorised share is large. A whole-spend donut would be one colour at today's 95%.
6. **Recent transactions:** the 6 newest from the existing transactions endpoint, with merchant initial, category
   and date.
7. **Insights:** deterministic only, from figures the API already returns (month-to-date vs last month, EMI interest,
   biggest category change). No invented advice.
8. **Top merchants:** bars sharing Money out as the denominator (Phase 4.2).
9. Keep the existing period selector and accessible table fallbacks for charts.

**Tests:** update `DashboardPage.test.tsx`; add an e2e check that the page renders at 390px and 1440px wide.

## Delivery

Three pull requests against `main`, each reviewed before the next, since `main` deploys to Vercel:

1. **Data fixes:** Phases 1–4 (backend, plus the Phase 4 frontend label changes).
2. **Small UI fixes:** Phase 5.
3. **Redesign:** Phase 6.

## Done when

- On the real account for 1 Sept – 21 Nov: own-account moves appear under "Moved between your accounts", Net matches
  its label, the Zomato and Urbanclap refunds net against their merchants, and "P2M URBANCLAP AIRTEL PA" is no longer
  Utilities.
- Uncategorised spending is measurably lower after Phase 3 (record the before/after percentage in PR 1).
- Backend tests, `npm run ci` and e2e pass.
- The redesigned Overview matches the mockup at desktop width and stacks cleanly on a phone.
