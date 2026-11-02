CREATE TABLE notifications (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(8)   NOT NULL,
    title      VARCHAR(150) NOT NULL,
    body       TEXT         NOT NULL,
    link       VARCHAR(255),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_notifications_user (user_id, is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_notifications_type CHECK (type IN ('HEALTH', 'CLAIM', 'SYSTEM'))
);

CREATE TABLE audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id    BIGINT,
    action      VARCHAR(60) NOT NULL,
    entity      VARCHAR(40) NOT NULL,
    entity_id   BIGINT,
    before_json JSON,
    after_json  JSON,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_audit_log_entity (entity, entity_id)
);

CREATE TABLE thresholds (
    threshold_key   VARCHAR(50) PRIMARY KEY,
    threshold_value DECIMAL(10,3),
    status          VARCHAR(12) NOT NULL DEFAULT 'TO_CONFIRM',
    description     VARCHAR(200) NOT NULL,
    updated_by      BIGINT,
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_thresholds_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT ck_thresholds_status CHECK (status IN ('TO_CONFIRM', 'CONFIRMED'))
);

-- Values are the suggestions in RINDA_PROJECT.md. NULL means the spec gives no value, so the team must supply one.
INSERT INTO thresholds (threshold_key, threshold_value, description) VALUES
    ('plot_min_area_ha',            0.100, 'Smallest plot allowed, in hectares (spec 3.2)'),
    ('plot_max_area_ha',            NULL,  'Largest plot allowed, in hectares (spec 5.2)'),
    ('plot_max_overlap_pct',        10.000,'Overlap with another farmer''s plot that flags an agent check (spec 5.2)'),
    ('min_clear_pixels',            20.000,'Clear pixels needed for a valid observation (spec 3.2)'),
    ('watch_rain_pct',              60.000,'30-day rain as a percent of normal below which a plot is WATCH (spec 3.4)'),
    ('at_risk_consecutive_obs',     2.000, 'Consecutive low observations needed for AT_RISK (spec 3.4)'),
    ('at_risk_min_days',            10.000,'Days below the band needed for AT_RISK (spec 3.4)'),
    ('at_risk_anomaly',             -1.000,'Anomaly score at or below which a plot is clearly low (spec 3.4)'),
    ('not_enough_data_window_days', 15.000,'Window checked for valid observations (spec 3.4)'),
    ('forecast_min_dap',            42.000,'Days after planting before a forecast is shown (spec 3.6)'),
    ('trigger_at_risk_days',        NULL,  'X: days AT_RISK in the sensitive stage needed to trigger a claim (spec 3.7)'),
    ('trigger_forecast_pct',        NULL,  'Y: forecast yield percent of normal below which a claim triggers (spec 3.7)'),
    ('trigger_rain_deficit_pct',    NULL,  'Z: rain deficit percent that triggers a claim (spec 3.7)'),
    ('sensitive_stage_start_dap',   50.000,'Start of the sensitive stage, days after planting (spec 3.7)'),
    ('sensitive_stage_end_dap',     100.000,'End of the sensitive stage, days after planting (spec 3.7)');
