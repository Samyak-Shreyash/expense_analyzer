# User Journeys

**Document ID:** PROD-002  
**Version:** 0.1  
**Depends on:** PRD.md, MVP-REQUIREMENTS.md

## 1. Journey: First-Time Setup
1. User opens product.
2. Registers/logs in.
3. Creates or selects a financial account.
4. Uploads a statement.
5. Sees immediate upload confirmation and processing status.
6. Returns to view transactions and analytics.

**Edge cases**
- Duplicate email/account.
- Invalid credentials.
- Session expiry.
- Unauthorized account access.
- User abandons upload.

## 2. Journey: CSV Upload
1. Select account.
2. Select CSV.
3. Server validates file.
4. Server calculates checksum.
5. Statement is created.
6. Processing job is queued.
7. Worker parses rows.
8. Transactions are validated, normalized, deduplicated, categorized.
9. Analytics are refreshed.
10. User sees completion.

**Edge cases**
- Empty file.
- Unsupported columns.
- Wrong delimiter.
- Mixed date formats.
- Negative/positive amount conventions.
- Invalid rows.
- Duplicate statement.
- Partial parsing failure.
- Worker retry.
- Worker crash.
- Very large file.

## 3. Journey: PDF Upload
1. User selects supported PDF.
2. File is validated and stored.
3. Parser extracts structured/text data.
4. Transactions follow the same normalization pipeline as CSV.

**Edge cases**
- Password-protected PDF.
- Scanned image-only PDF.
- Unsupported bank layout.
- Broken PDF.
- Ambiguous tables.
- Duplicate statement.

## 4. Journey: Understand Spending
1. User opens dashboard.
2. Selects date range.
3. Reviews total spending.
4. Drills into category.
5. Drills into merchant.
6. Reviews underlying transactions.
7. Corrects a transaction if needed.

## 5. Journey: Recurring Expenses
1. User opens recurring view.
2. Reviews detected recurring merchants.
3. Sees frequency, expected amount, confidence, and next expected date.
4. Opens supporting transactions.

**Edge cases**
- Variable amount subscription.
- Missed occurrence.
- Merchant name variation.
- Seasonal expense.
- False positive.

## 6. Journey: Anomaly Investigation
1. User sees unusual-spending insight.
2. Opens evidence.
3. Reviews comparison/baseline.
4. Opens transactions.
5. Determines whether activity is expected.

**Edge cases**
- Legitimate one-off purchase.
- Refund/reversal.
- Large transfer.
- New merchant with no history.

## 7. Journey: Compare Periods
1. User selects current and comparison periods.
2. Backend calculates totals.
3. User sees absolute and percentage change.
4. User drills into categories/merchants responsible.

## 8. Journey: Failed Processing
- User sees FAILED status.
- Safe error explains what happened without exposing parser internals or sensitive data.
- Original upload remains available where policy permits.
- Retry is possible when failure is transient.
- Unsupported formats are clearly identified.

## 9. Journey: Data Isolation
Every protected request resolves user identity and verifies ownership of account, statement, transaction, insight, and AI query resources. A guessed ID must never reveal another user's data.

## 10. Journey: Duplicate Upload
- Same file checksum is detected.
- Product either returns the existing statement or clearly marks the upload as duplicate.
- No duplicate transactions are created.

## 11. Journey: AI Question (MVP+)
1. User asks a supported financial question.
2. Intent is identified.
3. Authorization is checked.
4. A controlled analysis tool is called.
5. Backend returns verified data.
6. LLM explains the result.
7. Response identifies relevant period/evidence where useful.

AI failure must degrade gracefully to the underlying analytics rather than fabricate an answer.
