-- Migration: V21__cascade_user_merchant_aliases_and_votes_on_user_delete.sql
-- Description: Deleting a user deletes their merchant aliases and suggestion votes.

ALTER TABLE user_merchant_aliases DROP CONSTRAINT user_merchant_aliases_user_id_fkey;
ALTER TABLE user_merchant_aliases ADD CONSTRAINT user_merchant_aliases_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE merchant_suggestion_votes DROP CONSTRAINT merchant_suggestion_votes_user_id_fkey;
ALTER TABLE merchant_suggestion_votes ADD CONSTRAINT merchant_suggestion_votes_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
