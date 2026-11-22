# Overview accuracy report (spec)

Source: an audit of a real account (1,717 transactions, Aug–Nov 2026), received 2026-11-21. Verbatim findings below;
the plan `docs/superpowers/plans/2026-11-21-overview-accuracy.md` implements them.

## Classification
- Almost nothing is categorised: "Uncategorized" is 95% of spending, "Unmatched" is 97% of Top merchants. Domino's,
  KFC, Rameshwaram Cafe, Indian Railways, Policybazaar and Ekart stay uncategorised.
- Transfers between own accounts aren't detected (2 of 1,717). E.g. −₹40,000 "SAMYAK SHREYASH UTIB" and +₹40,000
  "SAMYAKSH HDFCB ANK" on 3 Aug; recurring +₹4,000 "Samyaksh" credits; −₹55,000 "P2M SAMYAK SHREYASH PAID V".
- Refunds and reversals aren't paired: Zomato refund +₹377.90 ("Zomato Me", uncategorised) vs the −₹377.90 order;
  Urbanclap refund; AU Small Finance ±₹80. ₹1 verification payments (CRADJ, Dreamplug, Cred, IDFC) count as real money.
- Wrong matches: "CRADJ ₹1.00" → WF Salary / Refund; "P2M URBANCLAP AIRTEL PA" → Airtel / Utilities (bank name picked).
- Names from wrong text: account number as a name ("631770008709" from "DUMMY NAME RENT"); notes in names
  ("Venkata Ramanamma See", "Sourav Ha"); cut-off names ("Policybazaar Insuranc", "Bhavya ."); one merchant under two
  names ("The Rames"/"The Rameshwaram Cafe", "Thesmartq1"/"SmartQ").
- Keywords ignored: "EMI SAMYAK SHREYASH" (−₹26,813), "…RENT…" (−₹36,500). No Rent, Insurance or Health categories.

## Overview maths
- Net ≠ Money in − Money out: Net includes the ₹1.66L loan and the ₹1.25L "not counted" outflow.
- Monthly cash-flow table adds the loan to November's money in.
- Same amount, different percentages: Blinkit 1.02% in "Where it went", 1.31% in "Top merchants" (different totals).
- "Not counted as spending" list covers ₹66,500 of ₹1,24,715 Loan EMI; Cred EMI missing; Denil and Shashank counted
  as Loan EMI.
- "Nov: 51.96% less than the month before" compares 21 days with a full month.
- Percentages rounded inconsistently ("28%" vs "22.26%").

## Smaller bugs
- Min > Max amount accepted silently.
- "Only transfers" puts `transfers=only&transfer=true` in the URL.
- Transfers dropdown has no linked label.
- Categories screen-reader text says "No, Loan counts as spending".
- Refund is "counts as spending: Yes".
- Top menu clipped at ~450px ("Categories").
- Statements copy claims "each payment is counted once, even if your files overlap", but cross-account duplicates
  are not caught.

Priority given by the reporter: transfer and refund pairing first, then Net.
