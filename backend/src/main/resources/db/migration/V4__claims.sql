-- payout_rate and amount stay NULL until the insurer's rates are confirmed.
-- Claims are only created by the trigger; a human decides each one in claim_decisions.
CREATE TABLE claims (
    id                     BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id                BIGINT      NOT NULL,
    season_id              BIGINT      NOT NULL,
    trigger_date           DATE        NOT NULL,
    status                 VARCHAR(24) NOT NULL DEFAULT 'UNDER_REVIEW',
    payout_rate            DECIMAL(6,4),
    amount                 DECIMAL(14,2),
    evidence_snapshot_json JSON        NOT NULL,
    model_version          VARCHAR(30) NOT NULL,
    is_demo                BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_claims_plot (plot_id),
    CONSTRAINT fk_claims_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_claims_season FOREIGN KEY (season_id) REFERENCES seasons (id),
    CONSTRAINT ck_claims_status CHECK (status IN ('UNDER_REVIEW', 'FIELD_VISIT_REQUESTED', 'APPROVED',
        'DECLINED', 'PAID', 'REVIEW_REQUESTED', 'FINAL'))
);

CREATE TABLE claim_decisions (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    claim_id   BIGINT      NOT NULL,
    officer_id BIGINT      NOT NULL,
    decision   VARCHAR(10) NOT NULL,
    reason     TEXT        NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_claim_decisions_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_claim_decisions_officer FOREIGN KEY (officer_id) REFERENCES users (id),
    CONSTRAINT ck_claim_decisions_decision CHECK (decision IN ('APPROVED', 'DECLINED')),
    CONSTRAINT ck_claim_decisions_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0)
);

CREATE TABLE review_requests (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    claim_id       BIGINT      NOT NULL,
    farmer_message TEXT        NOT NULL,
    status         VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    answer         TEXT,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_review_requests_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT ck_review_requests_status CHECK (status IN ('OPEN', 'ANSWERED'))
);

-- A report is evidence only. It never creates a claim or a payout.
CREATE TABLE problem_reports (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id               BIGINT      NOT NULL,
    farmer_id             BIGINT      NOT NULL,
    reason                VARCHAR(40) NOT NULL,
    note                  TEXT,
    photo_url             VARCHAR(255),
    agent_visit_requested BOOLEAN     NOT NULL DEFAULT FALSE,
    claim_id              BIGINT,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_problem_reports_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_problem_reports_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (user_id),
    CONSTRAINT fk_problem_reports_claim FOREIGN KEY (claim_id) REFERENCES claims (id)
);

CREATE TABLE field_visits (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    plot_id         BIGINT      NOT NULL,
    agent_id        BIGINT      NOT NULL,
    checklist_json  JSON,
    plant_condition VARCHAR(40),
    observed_cause  VARCHAR(40),
    growth_stage    VARCHAR(40),
    photo_url       VARCHAR(255),
    lat             DECIMAL(9,6),
    lng             DECIMAL(9,6),
    visited_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_field_visits_plot FOREIGN KEY (plot_id) REFERENCES plots (id),
    CONSTRAINT fk_field_visits_agent FOREIGN KEY (agent_id) REFERENCES agents (user_id)
);

CREATE TABLE payout_accounts (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    provider   VARCHAR(10) NOT NULL,
    msisdn     VARCHAR(16) NOT NULL,
    verified   BOOLEAN     NOT NULL DEFAULT FALSE,
    name_match BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_payout_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payout_accounts_provider CHECK (provider IN ('MTN_MOMO', 'AIRTEL'))
);

-- Rinda never moves real money, so a non-simulated payout row cannot exist.
CREATE TABLE payouts (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    claim_id     BIGINT        NOT NULL,
    account_id   BIGINT        NOT NULL,
    amount       DECIMAL(14,2) NOT NULL,
    status       VARCHAR(10)   NOT NULL,
    reference    VARCHAR(40)   NOT NULL,
    is_simulated BOOLEAN       NOT NULL DEFAULT TRUE,
    is_demo      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payouts_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    CONSTRAINT fk_payouts_account FOREIGN KEY (account_id) REFERENCES payout_accounts (id),
    CONSTRAINT ck_payouts_simulated CHECK (is_simulated = TRUE),
    CONSTRAINT ck_payouts_status CHECK (status IN ('PENDING', 'PAID', 'FAILED'))
);
