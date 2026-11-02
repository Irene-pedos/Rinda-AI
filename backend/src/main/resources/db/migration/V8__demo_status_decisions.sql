-- Team decisions for docs/OPEN_QUESTIONS.md questions 15 to 17.
-- CONFIRMED_FOR_DEMO means agreed for the presentation, but not a final insurer value.
ALTER TABLE thresholds
    DROP CHECK ck_thresholds_status,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'TO_CONFIRM',
    ADD CONSTRAINT ck_thresholds_status CHECK (status IN ('TO_CONFIRM', 'CONFIRMED_FOR_DEMO', 'CONFIRMED'));

-- NULL only on rows saved before this migration.
ALTER TABLE plot_status ADD COLUMN thresholds_json JSON;

-- Threshold changes are audited. The actor is NULL because a migration made them.
INSERT INTO audit_log (action, entity, before_json, after_json)
SELECT 'THRESHOLD_CHANGED', 'thresholds',
       JSON_OBJECT('key', t.threshold_key, 'value', t.threshold_value, 'status', t.status),
       JSON_OBJECT('key', t.threshold_key, 'value', c.new_value, 'status', 'CONFIRMED_FOR_DEMO')
FROM thresholds t
JOIN (
    SELECT 'min_clear_pixels' AS threshold_key, 20.000 AS new_value
    UNION ALL SELECT 'not_enough_data_window_days', 20.000
    UNION ALL SELECT 'not_enough_data_min_obs', 1.000
    UNION ALL SELECT 'watch_rain_pct', 60.000
    UNION ALL SELECT 'at_risk_anomaly', -1.500
    UNION ALL SELECT 'at_risk_consecutive_obs', 2.000
    UNION ALL SELECT 'at_risk_min_days', 10.000
) c ON c.threshold_key = t.threshold_key;

UPDATE thresholds SET threshold_value = 20.000, status = 'CONFIRMED_FOR_DEMO',
    description = 'Clear pixels needed after the 10 m inward buffer for a valid observation (question 15)'
WHERE threshold_key = 'min_clear_pixels';

UPDATE thresholds SET threshold_value = 20.000, status = 'CONFIRMED_FOR_DEMO',
    description = 'Days checked for valid observations (question 15)'
WHERE threshold_key = 'not_enough_data_window_days';

UPDATE thresholds SET threshold_value = 1.000, status = 'CONFIRMED_FOR_DEMO',
    description = 'Valid observations needed in the window, or the status is NOT_ENOUGH_DATA (question 15)'
WHERE threshold_key = 'not_enough_data_min_obs';

UPDATE thresholds SET threshold_value = 60.000, status = 'CONFIRMED_FOR_DEMO'
WHERE threshold_key = 'watch_rain_pct';

UPDATE thresholds SET threshold_value = -1.500, status = 'CONFIRMED_FOR_DEMO',
    description = 'Anomaly at or below which an observation meets the AT_RISK condition (question 16)'
WHERE threshold_key = 'at_risk_anomaly';

UPDATE thresholds SET threshold_value = 2.000, status = 'CONFIRMED_FOR_DEMO',
    description = 'Consecutive valid observations meeting the AT_RISK condition (question 17)'
WHERE threshold_key = 'at_risk_consecutive_obs';

UPDATE thresholds SET threshold_value = 10.000, status = 'CONFIRMED_FOR_DEMO',
    description = 'Days those observations must span, first to last. Applies together with the count (question 17)'
WHERE threshold_key = 'at_risk_min_days';

INSERT INTO thresholds (threshold_key, threshold_value, status, description) VALUES
    ('watch_anomaly',         -1.000, 'CONFIRMED_FOR_DEMO', 'Anomaly below which the latest valid observation is WATCH (question 16)'),
    ('trigger_min_valid_obs',  2.000, 'CONFIRMED_FOR_DEMO', 'Valid observations a claim trigger needs (question 15)');

INSERT INTO audit_log (action, entity, after_json)
SELECT 'THRESHOLD_CHANGED', 'thresholds',
       JSON_OBJECT('key', threshold_key, 'value', threshold_value, 'status', status)
FROM thresholds
WHERE threshold_key IN ('watch_anomaly', 'trigger_min_valid_obs');
