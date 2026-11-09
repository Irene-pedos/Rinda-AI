-- Field evidence: farmer problem reports and agent field visits (spec §2.2 #1, §9.6, §9.8).
-- Neither ever creates a claim or a payout. Both link to the plot's open claim, if there is one.

-- Why an agent rejected a plot boundary, shown to the farmer.
ALTER TABLE plots ADD COLUMN rejection_reason VARCHAR(500);

-- client_id lets the phone's offline outbox resend a report safely: the same id is stored once.
-- Reasons are what a farmer can see. Satellites can't identify pests or diseases, so there are no such causes.
ALTER TABLE problem_reports
    ADD COLUMN client_id   VARCHAR(40),
    ADD COLUMN reported_at DATETIME(6),
    ADD CONSTRAINT uq_problem_reports_client UNIQUE (farmer_id, client_id),
    ADD CONSTRAINT ck_problem_reports_reason CHECK (reason IN ('DRY_LEAVES', 'YELLOW_LEAVES', 'FLOODING', 'OTHER'));

ALTER TABLE field_visits
    ADD COLUMN notes      TEXT,
    ADD COLUMN client_id  VARCHAR(40),
    ADD COLUMN claim_id   BIGINT,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uq_field_visits_client UNIQUE (agent_id, client_id),
    ADD CONSTRAINT fk_field_visits_claim FOREIGN KEY (claim_id) REFERENCES claims (id),
    ADD CONSTRAINT ck_field_visits_condition CHECK (plant_condition IN ('GOOD', 'STRESSED', 'SEVERE_LOSS', 'FAILED')),
    ADD CONSTRAINT ck_field_visits_cause CHECK (observed_cause IN ('WATER_DEFICIT', 'EXCESS_WATER', 'NUTRIENT', 'OTHER')),
    ADD CONSTRAINT ck_field_visits_stage CHECK (growth_stage IN ('EMERGENCE', 'VEGETATIVE', 'FLOWERING', 'GRAIN', 'MATURITY'));
