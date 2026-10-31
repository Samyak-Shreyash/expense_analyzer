-- Migration: V20__allow_duplicate_person_names.sql
-- Description: Two of a user's people may share a name (the UI offers a merge instead); keep the lookup index, non-unique.

DROP INDEX IF EXISTS uq_people_user_lower_name;
CREATE INDEX IF NOT EXISTS idx_people_user_lower_name ON people(user_id, lower(name));
