-- A forecast row can say why there is no range yet, so the newest row is always the truth.
ALTER TABLE harvest_forecasts
    MODIFY COLUMN low_pct DECIMAL(6,2),
    MODIFY COLUMN high_pct DECIMAL(6,2),
    ADD COLUMN reason VARCHAR(20),
    ADD COLUMN data_quality_note TEXT NOT NULL,
    ADD COLUMN thresholds_json JSON NOT NULL,
    ADD CONSTRAINT ck_harvest_forecasts_reason CHECK (reason IS NULL OR reason IN ('TOO_EARLY', 'NOT_ENOUGH_DATA')),
    ADD CONSTRAINT ck_harvest_forecasts_available
        CHECK ((reason IS NULL) = (low_pct IS NOT NULL AND high_pct IS NOT NULL));

-- The spec gives no minimum, so this is a suggestion for the team to confirm (question 20).
INSERT INTO thresholds (threshold_key, threshold_value, description) VALUES
    ('forecast_min_bins', 3.000, '10-day periods with a reading and a normal band that a forecast needs (spec 3.6)');
