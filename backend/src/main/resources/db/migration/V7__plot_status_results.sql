-- Results keep their origin and the run that made them, so each can be labelled and reproduced.
ALTER TABLE plot_status
    ADD COLUMN data_origin VARCHAR(12) NOT NULL,
    ADD COLUMN run_id      BIGINT      NOT NULL,
    MODIFY COLUMN anomaly DECIMAL(10,3),
    ADD CONSTRAINT ck_plot_status_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE')),
    ADD CONSTRAINT ck_plot_status_cause CHECK (cause IS NULL OR cause IN ('WATER_DEFICIT', 'UNEXPLAINED')),
    ADD CONSTRAINT fk_plot_status_run FOREIGN KEY (run_id) REFERENCES ml_runs (id);

ALTER TABLE harvest_forecasts
    ADD COLUMN data_origin VARCHAR(12) NOT NULL,
    ADD COLUMN run_id      BIGINT      NOT NULL,
    ADD CONSTRAINT ck_harvest_forecasts_origin CHECK (data_origin IN ('GEE', 'SAMPLE_FILE')),
    ADD CONSTRAINT fk_harvest_forecasts_run FOREIGN KEY (run_id) REFERENCES ml_runs (id);

-- The spec says "too few valid observations" without a number. 1 is the mildest reading.
INSERT INTO thresholds (threshold_key, threshold_value, description) VALUES
    ('not_enough_data_min_obs', 1.000, 'Valid observations needed in the window, or the status is NOT_ENOUGH_DATA (spec 3.4)');
