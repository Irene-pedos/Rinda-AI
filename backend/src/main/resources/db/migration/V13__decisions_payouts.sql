-- A code sent for one purpose can't be used for another: a login code never verifies a payout number.
ALTER TABLE otp_codes
    ADD COLUMN purpose VARCHAR(10) NOT NULL DEFAULT 'LOGIN',
    ADD CONSTRAINT ck_otp_codes_purpose CHECK (purpose IN ('LOGIN', 'PAYOUT')),
    ADD KEY ix_otp_codes_phone_purpose (phone, purpose, created_at);

-- One payout account per user. Changing the number clears 'verified', so it must be verified again (spec §2.2 #12).
ALTER TABLE payout_accounts
    ADD COLUMN verified_at DATETIME(6),
    ADD COLUMN updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uq_payout_accounts_user UNIQUE (user_id);

-- One review request per decision (spec §3.7): each request points at the decision it questions.
ALTER TABLE review_requests
    ADD COLUMN decision_id BIGINT NOT NULL,
    ADD COLUMN farmer_id   BIGINT NOT NULL,
    ADD COLUMN answered_at DATETIME(6),
    ADD CONSTRAINT uq_review_requests_decision UNIQUE (decision_id),
    ADD CONSTRAINT fk_review_requests_decision FOREIGN KEY (decision_id) REFERENCES claim_decisions (id),
    ADD CONSTRAINT fk_review_requests_farmer FOREIGN KEY (farmer_id) REFERENCES farmers (user_id);

-- A decision made in answer to a review says which request it answers.
ALTER TABLE claim_decisions
    ADD COLUMN answers_review_id BIGINT,
    ADD CONSTRAINT fk_claim_decisions_review FOREIGN KEY (answers_review_id) REFERENCES review_requests (id);

-- One simulated payout per claim.
ALTER TABLE payouts
    ADD CONSTRAINT uq_payouts_claim UNIQUE (claim_id);
