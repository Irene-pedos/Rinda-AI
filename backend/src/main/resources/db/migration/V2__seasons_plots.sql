-- Season dates stay NULL until the team confirms them (docs/OPEN_QUESTIONS.md).
CREATE TABLE seasons (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(10) NOT NULL,
    type        CHAR(1)     NOT NULL,
    start_date  DATE,
    end_date    DATE,
    data_status VARCHAR(12) NOT NULL DEFAULT 'TO_CONFIRM',
    is_demo     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_seasons_code (code),
    CONSTRAINT ck_seasons_type CHECK (type IN ('A', 'B')),
    CONSTRAINT ck_seasons_data_status CHECK (data_status IN ('TO_CONFIRM', 'CONFIRMED'))
);

-- boundary is lat/lon SRID 4326; write it with ST_GeomFromGeoJSON, which handles the axis order.
-- planting_date is NULL only for seeded placeholder plots; the API always requires it.
CREATE TABLE plots (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    farmer_id     BIGINT        NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    crop          VARCHAR(10)   NOT NULL DEFAULT 'MAIZE',
    boundary      POLYGON       NOT NULL SRID 4326,
    geojson       TEXT          NOT NULL,
    area_ha       DECIMAL(10,4) NOT NULL,
    planting_date DATE,
    status        VARCHAR(24)   NOT NULL DEFAULT 'PENDING_VERIFICATION',
    verified_by   BIGINT,
    data_status   VARCHAR(12)   NOT NULL DEFAULT 'CONFIRMED',
    is_demo       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    SPATIAL KEY ix_plots_boundary (boundary),
    KEY ix_plots_farmer (farmer_id),
    CONSTRAINT fk_plots_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (user_id),
    CONSTRAINT fk_plots_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT ck_plots_crop CHECK (crop = 'MAIZE'),
    CONSTRAINT ck_plots_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'REJECTED', 'ARCHIVED')),
    CONSTRAINT ck_plots_data_status CHECK (data_status IN ('TO_CONFIRM', 'CONFIRMED'))
);

-- sum_insured and premium stay NULL until the insurer confirms them.
CREATE TABLE enrollments (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id     BIGINT      NOT NULL,
    season_id   BIGINT      NOT NULL,
    status      VARCHAR(12) NOT NULL,
    sum_insured DECIMAL(14,2),
    premium     DECIMAL(14,2),
    start_date  DATE,
    end_date    DATE,
    is_demo     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_enrollments_plot_season (plot_id, season_id),
    CONSTRAINT fk_enrollments_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_enrollments_season FOREIGN KEY (season_id) REFERENCES seasons (id),
    CONSTRAINT ck_enrollments_status CHECK (status IN ('PENDING', 'COVERED', 'CANCELLED'))
);
