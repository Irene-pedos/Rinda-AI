-- "Run analysis" (spec §10.1): an admin, or the optional 5-day schedule, asks the ML service to analyse plots.
-- Each run records which plots it covered and what happened to each, so failures are visible and retried.
CREATE TABLE analysis_runs (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    source       VARCHAR(10) NOT NULL,
    status       VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
    trigger_type VARCHAR(10) NOT NULL,
    requested_by BIGINT,
    as_of        DATE,
    plots_total  INT         NOT NULL DEFAULT 0,
    plots_done   INT         NOT NULL DEFAULT 0,
    plots_failed INT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at   DATETIME(6),
    finished_at  DATETIME(6),
    CONSTRAINT fk_analysis_runs_user FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_analysis_runs_source CHECK (source IN ('GEE', 'SAMPLE')),
    CONSTRAINT ck_analysis_runs_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT ck_analysis_runs_trigger CHECK (trigger_type IN ('MANUAL', 'SCHEDULE'))
);

CREATE TABLE analysis_run_items (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id      BIGINT       NOT NULL,
    plot_id     BIGINT       NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'QUEUED',
    attempts    INT          NOT NULL DEFAULT 0,
    error_code  VARCHAR(40),
    error       VARCHAR(500),
    ml_run_id   BIGINT,
    finished_at DATETIME(6),
    UNIQUE KEY uq_analysis_run_items (run_id, plot_id),
    CONSTRAINT fk_analysis_run_items_run FOREIGN KEY (run_id) REFERENCES analysis_runs (id),
    CONSTRAINT fk_analysis_run_items_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_analysis_run_items_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'SKIPPED'))
);
