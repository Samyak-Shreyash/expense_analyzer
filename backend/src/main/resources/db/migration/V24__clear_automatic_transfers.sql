-- Migration: V24__clear_automatic_transfers.sql
-- Description: Transfers are only ever marked by the user, so undoes every automatic pairing. Marks the user made stay.

UPDATE transactions
SET transfer = FALSE, transfer_pair_id = NULL
WHERE transfer = TRUE AND transfer_manual = FALSE;
