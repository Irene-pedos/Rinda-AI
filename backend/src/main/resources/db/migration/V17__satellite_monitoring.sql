-- Satellite monitoring (docs/02-satellite-monitoring.md). It starts once a plot has a walked boundary: a BACKFILL
-- job fetches from the planting date to today, then a weekly REFRESH fetches the new images. Raw values are kept
-- per observation, tied to the walk they were measured for, so a new walk starts a fresh series and the rules can
-- change later without fetching again. A satellite result is a review flag, never proof of crop.

CREATE TABLE satellite_jobs (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT       NOT NULL,
    walk_id       BIGINT       NOT NULL,
    kind          VARCHAR(10)  NOT NULL,
    source        VARCHAR(10)  NOT NULL,
    trigger_type  VARCHAR(10)  NOT NULL,
    status        VARCHAR(12)  NOT NULL DEFAULT 'QUEUED',
    -- Null on SAMPLE jobs until they finish: the simulated file sets its own dates.
    fetch_from    DATE,
    fetch_to      DATE,
    attempts      INT          NOT NULL DEFAULT 0,
    images_found  INT,
    data_origin   VARCHAR(12),
    model_version VARCHAR(30),
    error_code    VARCHAR(40),
    error         VARCHAR(500),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at    DATETIME(6),
    finished_at   DATETIME(6),
    KEY ix_satellite_jobs_plot (plot_id, status),
    CONSTRAINT fk_satellite_jobs_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_satellite_jobs_walk FOREIGN KEY (walk_id) REFERENCES gps_walks (id),
    CONSTRAINT ck_satellite_jobs_kind CHECK (kind IN ('BACKFILL', 'REFRESH')),
    CONSTRAINT ck_satellite_jobs_source CHECK (source IN ('GEE', 'SAMPLE')),
    CONSTRAINT ck_satellite_jobs_trigger CHECK (trigger_type IN ('BOUNDARY', 'SCHEDULE', 'MANUAL')),
    CONSTRAINT ck_satellite_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED', 'INTERRUPTED'))
);

-- One row per image date. ndvi_median is the raw median of the clear pixels, with no pixel limit applied:
-- the limit is applied when the series is read, so it can change without fetching again.
CREATE TABLE satellite_observations (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id         BIGINT       NOT NULL,
    walk_id         BIGINT       NOT NULL,
    obs_date        DATE         NOT NULL,
    source          VARCHAR(12)  NOT NULL,
    data_origin     VARCHAR(12)  NOT NULL,
    ndvi_median     DECIMAL(6,4),
    pixel_count     INT          NOT NULL,
    cloud_fraction  DECIMAL(5,4),
    scene_cloud_pct DECIMAL(5,2),
    job_id          BIGINT       NOT NULL,
    model_version   VARCHAR(30)  NOT NULL,
    fetched_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_satellite_observations (walk_id, obs_date, source),
    KEY ix_satellite_observations_plot (plot_id),
    CONSTRAINT fk_satellite_observations_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_satellite_observations_walk FOREIGN KEY (walk_id) REFERENCES gps_walks (id),
    CONSTRAINT fk_satellite_observations_job FOREIGN KEY (job_id) REFERENCES satellite_jobs (id),
    CONSTRAINT ck_satellite_observations_source CHECK (source IN ('S2'))
);

CREATE TABLE satellite_rainfall (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id     BIGINT       NOT NULL,
    walk_id     BIGINT       NOT NULL,
    obs_date    DATE         NOT NULL,
    source      VARCHAR(12)  NOT NULL,
    data_origin VARCHAR(12)  NOT NULL,
    rain_mm     DECIMAL(7,2) NOT NULL,
    job_id      BIGINT       NOT NULL,
    fetched_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_satellite_rainfall (walk_id, obs_date, source),
    KEY ix_satellite_rainfall_plot (plot_id),
    CONSTRAINT fk_satellite_rainfall_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_satellite_rainfall_walk FOREIGN KEY (walk_id) REFERENCES gps_walks (id),
    CONSTRAINT fk_satellite_rainfall_job FOREIGN KEY (job_id) REFERENCES satellite_jobs (id),
    CONSTRAINT ck_satellite_rainfall_source CHECK (source IN ('CHIRPS'))
);

-- "Consistent with a maize season?" after each fetch, with the model version and settings it used.
CREATE TABLE season_checks (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id          BIGINT       NOT NULL,
    walk_id          BIGINT       NOT NULL,
    job_id           BIGINT       NOT NULL,
    check_date       DATE         NOT NULL,
    planting_date    DATE         NOT NULL,
    flag             VARCHAR(16)  NOT NULL,
    reason           VARCHAR(40)  NOT NULL,
    confidence       VARCHAR(6)   NOT NULL,
    images_used      INT          NOT NULL,
    pixels_used      INT,
    latest_image_date DATE,
    explanation_json JSON,
    params_json      JSON,
    data_origin      VARCHAR(12)  NOT NULL,
    model_version    VARCHAR(30)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_season_checks_plot (plot_id),
    CONSTRAINT fk_season_checks_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_season_checks_walk FOREIGN KEY (walk_id) REFERENCES gps_walks (id),
    CONSTRAINT fk_season_checks_job FOREIGN KEY (job_id) REFERENCES satellite_jobs (id),
    CONSTRAINT ck_season_checks_flag CHECK (flag IN ('CONSISTENT', 'INCONSISTENT', 'NOT_ENOUGH_DATA')),
    CONSTRAINT ck_season_checks_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);
