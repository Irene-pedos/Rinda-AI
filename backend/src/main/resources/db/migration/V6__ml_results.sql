-- data_origin keeps sample data apart from real satellite data on every row.
ALTER TABLE observations
    ADD COLUMN data_origin VARCHAR(12) NOT NULL,
    ADD CONSTRAINT ck_observations_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE'));

ALTER TABLE weather_daily
    ADD COLUMN data_origin VARCHAR(12) NOT NULL,
    ADD CONSTRAINT ck_weather_daily_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE'));

ALTER TABLE baselines
    ADD COLUMN data_origin VARCHAR(12) NOT NULL,
    ADD CONSTRAINT ck_baselines_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE'));

-- One row per ML run, so any result can be reproduced: what ran, on which dates, with which settings.
CREATE TABLE ml_runs (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT      NOT NULL,
    data_origin   VARCHAR(12) NOT NULL,
    model_version VARCHAR(30) NOT NULL,
    planting_date DATE        NOT NULL,
    input_from    DATE        NOT NULL,
    input_to      DATE        NOT NULL,
    params_json   JSON        NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_ml_runs_plot (plot_id),
    CONSTRAINT fk_ml_runs_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_ml_runs_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE'))
);
