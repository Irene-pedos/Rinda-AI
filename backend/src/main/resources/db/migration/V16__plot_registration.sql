-- Plot registration without free drawing (docs/01-plot-verification.md, docs/03-data-model-and-api.md).
-- The boundary now comes only from a GPS boundary walk, so a new plot has no boundary until it is walked.

-- A SPATIAL index needs a NOT NULL column. Overlap checks scan the few plots near a new one, so the index
-- is dropped for now (docs/DECISIONS.md, 2026-11-11).
ALTER TABLE plots DROP INDEX ix_plots_boundary;

ALTER TABLE plots
    MODIFY boundary POLYGON SRID 4326 NULL,
    MODIFY geojson TEXT NULL,
    MODIFY area_ha DECIMAL(10,4) NULL,
    -- Typed by the farmer from their e-title. Validated loosely: the format is not confirmed (OPEN_QUESTIONS 23).
    ADD COLUMN upi VARCHAR(40) NULL AFTER farmer_id,
    ADD COLUMN verification_level VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED' AFTER status,
    ADD COLUMN relationship_to_plot VARCHAR(16) NULL,
    ADD COLUMN declaration_accepted_at DATETIME(6) NULL,
    -- Planted area inside the walked boundary. NULL means the whole boundary (a later optional second walk).
    ADD COLUMN planted_area_geojson TEXT NULL,
    ADD COLUMN walked_area_m2 DECIMAL(12,1) NULL,
    -- Typed by the farmer from the document, not read from it (no OCR). The agent compares it with the image.
    ADD COLUMN document_area_m2 DECIMAL(12,1) NULL,
    ADD COLUMN area_diff_pct DECIMAL(8,2) NULL,
    ADD KEY ix_plots_upi (upi),
    DROP CHECK ck_plots_status,
    ADD CONSTRAINT ck_plots_status CHECK (status IN ('DRAFT', 'PENDING_VERIFICATION', 'FLAGGED', 'DISPUTED',
        'ACTIVE', 'REJECTED', 'ARCHIVED')),
    ADD CONSTRAINT ck_plots_verification_level CHECK (verification_level IN ('SELF_DECLARED', 'DOCUMENT_SUPPORTED',
        'FIELD_VERIFIED', 'NLA_VERIFIED')),
    ADD CONSTRAINT ck_plots_relationship CHECK (relationship_to_plot IS NULL
        OR relationship_to_plot IN ('OWNER', 'FAMILY_MEMBER', 'TENANT'));

-- The farmer's proof of the plot. Personal data: served only after a visibility check, never logged.
CREATE TABLE plot_documents (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id       BIGINT       NOT NULL,
    type          VARCHAR(20)  NOT NULL,
    storage_key   VARCHAR(200) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    typed_area_m2 DECIMAL(12,1),
    uploaded_by   BIGINT       NOT NULL,
    uploaded_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    is_demo       BOOLEAN      NOT NULL DEFAULT FALSE,
    KEY ix_plot_documents_plot (plot_id),
    CONSTRAINT fk_plot_documents_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_plot_documents_user FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_plot_documents_type CHECK (type IN ('E_TITLE', 'PORTAL_SCREENSHOT', 'LEASE', 'OWNER_CONSENT'))
);

-- Every walk is kept with its raw points; the latest accepted one is CURRENT and gives the plot its boundary.
CREATE TABLE gps_walks (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id        BIGINT        NOT NULL,
    status         VARCHAR(12)   NOT NULL DEFAULT 'CURRENT',
    point_count    INT           NOT NULL,
    worst_accuracy_m DECIMAL(7,2) NOT NULL,
    mock_detected  BOOLEAN       NOT NULL,
    walked_area_m2 DECIMAL(12,1) NOT NULL,
    started_at     DATETIME(6)   NOT NULL,
    ended_at       DATETIME(6)   NOT NULL,
    submitted_by   BIGINT        NOT NULL,
    submitted_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_gps_walks_plot (plot_id),
    CONSTRAINT fk_gps_walks_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_gps_walks_user FOREIGN KEY (submitted_by) REFERENCES users (id),
    CONSTRAINT ck_gps_walks_status CHECK (status IN ('CURRENT', 'SUPERSEDED'))
);

CREATE TABLE gps_walk_points (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    walk_id       BIGINT        NOT NULL,
    seq           INT           NOT NULL,
    lat           DECIMAL(10,7) NOT NULL,
    lng           DECIMAL(10,7) NOT NULL,
    accuracy_m    DECIMAL(7,2)  NOT NULL,
    recorded_at   DATETIME(6)   NOT NULL,
    mock_location BOOLEAN       NOT NULL,
    UNIQUE KEY uq_gps_walk_points (walk_id, seq),
    CONSTRAINT fk_gps_walk_points_walk FOREIGN KEY (walk_id) REFERENCES gps_walks (id)
);

-- Review flags. Only a person resolves one; nothing is auto-resolved or auto-rejected.
CREATE TABLE plot_flags (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id         BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    details         VARCHAR(500) NOT NULL,
    related_plot_id BIGINT,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at     DATETIME(6),
    resolved_by     BIGINT,
    resolution_note VARCHAR(1000),
    KEY ix_plot_flags_plot (plot_id),
    CONSTRAINT fk_plot_flags_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_plot_flags_related FOREIGN KEY (related_plot_id) REFERENCES plots (id),
    CONSTRAINT fk_plot_flags_resolved_by FOREIGN KEY (resolved_by) REFERENCES users (id),
    CONSTRAINT ck_plot_flags_type CHECK (type IN ('OUTSIDE_ADMIN_AREA', 'AREA_MISMATCH', 'AREA_UNCHECKED',
        'GPS_ACCURACY_UNCHECKED', 'MOCK_LOCATION', 'OVERLAP', 'DUPLICATE_UPI', 'NON_OWNER',
        'LEGACY_DRAWN_BOUNDARY', 'SATELLITE_INCONSISTENT', 'OTHER')),
    CONSTRAINT ck_plot_flags_resolution CHECK ((resolved_at IS NULL) = (resolved_by IS NULL))
);

-- A person's check at the plot, with a photo whose location the verifier's app recorded.
CREATE TABLE plot_verifications (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id                BIGINT        NOT NULL,
    verifier_id            BIGINT        NOT NULL,
    method                 VARCHAR(20)   NOT NULL DEFAULT 'AGENT_VISIT',
    result                 VARCHAR(10)   NOT NULL,
    photo_key              VARCHAR(200),
    photo_lat              DECIMAL(10,7),
    photo_lng              DECIMAL(10,7),
    photo_accuracy_m       DECIMAL(7,2),
    photo_taken_at         DATETIME(6),
    distance_to_boundary_m DECIMAL(9,1),
    note                   VARCHAR(500),
    verified_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_plot_verifications_plot (plot_id),
    CONSTRAINT fk_plot_verifications_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_plot_verifications_verifier FOREIGN KEY (verifier_id) REFERENCES users (id),
    CONSTRAINT ck_plot_verifications_method CHECK (method IN ('AGENT_VISIT')),
    CONSTRAINT ck_plot_verifications_result CHECK (result IN ('VERIFIED', 'REJECTED'))
);

-- NULL until the team supplies a value. While NULL, each check fails safe: it sends the plot to review.
INSERT INTO thresholds (threshold_key, threshold_value, description) VALUES
    ('walk_max_accuracy_m',              NULL, 'Worst GPS accuracy accepted on a boundary walk point, in metres'),
    ('plot_area_mismatch_pct',           NULL, 'Walked vs document area difference above which a plot is flagged, in %'),
    ('verification_photo_max_distance_m',NULL, 'Furthest a verification photo may be from the walked boundary, in m');

-- Existing plots were drawn by hand. Demo plots are fictional placeholders and keep working for the demo,
-- labelled Demo. Real ones must be walked again: their drawn boundary stays as a record but is not trusted.
UPDATE plots SET verification_level = 'FIELD_VERIFIED' WHERE is_demo = TRUE AND status = 'ACTIVE';

INSERT INTO plot_flags (plot_id, type, details)
SELECT id, 'LEGACY_DRAWN_BOUNDARY', 'This boundary was drawn on a map before GPS walks. Walk the plot to replace it.'
FROM plots WHERE is_demo = FALSE AND status NOT IN ('ARCHIVED', 'REJECTED');

UPDATE plots SET status = 'FLAGGED', verification_level = 'SELF_DECLARED'
WHERE is_demo = FALSE AND status NOT IN ('ARCHIVED', 'REJECTED');
