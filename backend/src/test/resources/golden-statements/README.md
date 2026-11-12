# Golden statements

Each `<name>.<csv|xlsx|pdf>` has a `<name>.expected.csv` (`date,description,type,amount`) listing every
transaction the parser must extract, in order. `GoldenStatementAccuracyTest` scores field accuracy per
format and fails below the PRD target (98%).

Files here are synthetic. To add a real bank export, anonymize it first: replace names, UPI ids,
account and card numbers, and reference numbers. Never commit a real statement.
