-- Bins estimated from their neighbours are marked, so they are never mistaken for measured ones.
ALTER TABLE baselines ADD COLUMN is_filled BOOLEAN NOT NULL DEFAULT FALSE;
