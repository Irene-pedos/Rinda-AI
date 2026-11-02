CREATE TABLE model_versions (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    name         VARCHAR(60) NOT NULL,
    version      VARCHAR(30) NOT NULL,
    params_json  JSON,
    metrics_json JSON,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_model_versions (name, version)
);

CREATE TABLE observations (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT      NOT NULL,
    obs_date      DATE        NOT NULL,
    source        VARCHAR(12) NOT NULL,
    ndvi          DECIMAL(6,4),
    ndre          DECIMAL(6,4),
    ndmi          DECIMAL(6,4),
    vv            DECIMAL(7,3),
    vh            DECIMAL(7,3),
    clear_pixels  INT,
    cloud_pct     DECIMAL(5,2),
    smoothed_ndvi DECIMAL(6,4),
    UNIQUE KEY uq_observations (plot_id, obs_date, source),
    CONSTRAINT fk_observations_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_observations_source CHECK (source IN ('S2', 'S1', 'S1_ESTIMATE'))
);

CREATE TABLE weather_daily (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT NOT NULL,
    obs_date      DATE   NOT NULL,
    rain_mm       DECIMAL(7,2),
    soil_moisture DECIMAL(6,4),
    UNIQUE KEY uq_weather_daily (plot_id, obs_date),
    CONSTRAINT fk_weather_daily_plot FOREIGN KEY (plot_id) REFERENCES plots (id)
);

-- MVP baselines come from the plot's own past seasons (plot_id set).
-- The full design uses district baselines (district set, plot_id NULL).
CREATE TABLE baselines (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id     BIGINT,
    district    VARCHAR(80),
    season_type CHAR(1)     NOT NULL,
    dap_bin     INT         NOT NULL,
    p10         DECIMAL(6,4) NOT NULL,
    p50         DECIMAL(6,4) NOT NULL,
    p90         DECIMAL(6,4) NOT NULL,
    version     VARCHAR(30) NOT NULL,
    UNIQUE KEY uq_baselines (plot_id, district, season_type, dap_bin, version),
    CONSTRAINT fk_baselines_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_baselines_scope CHECK (plot_id IS NOT NULL OR district IS NOT NULL)
);

CREATE TABLE plot_status (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id          BIGINT      NOT NULL,
    status_date      DATE        NOT NULL,
    status           VARCHAR(16) NOT NULL,
    anomaly          DECIMAL(7,3),
    rain_pct         DECIMAL(6,2),
    cause            VARCHAR(30),
    confidence       VARCHAR(6)  NOT NULL,
    explanation_json JSON,
    model_version    VARCHAR(30) NOT NULL,
    UNIQUE KEY uq_plot_status (plot_id, status_date, model_version),
    CONSTRAINT fk_plot_status_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_plot_status_status CHECK (status IN ('HEALTHY', 'WATCH', 'AT_RISK', 'NOT_ENOUGH_DATA')),
    CONSTRAINT ck_plot_status_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE TABLE harvest_forecasts (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT       NOT NULL,
    forecast_date DATE         NOT NULL,
    low_pct       DECIMAL(6,2) NOT NULL,
    high_pct      DECIMAL(6,2) NOT NULL,
    model_version VARCHAR(30)  NOT NULL,
    confidence    VARCHAR(6)   NOT NULL,
    UNIQUE KEY uq_harvest_forecasts (plot_id, forecast_date, model_version),
    CONSTRAINT fk_harvest_forecasts_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT ck_harvest_forecasts_range CHECK (low_pct <= high_pct),
    CONSTRAINT ck_harvest_forecasts_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW'))
);
