-- The parametric trigger creates at most one automatic claim per plot and season.
-- A re-run of the analysis therefore can never create a second claim.
ALTER TABLE claims
    ADD CONSTRAINT uq_claims_plot_season UNIQUE (plot_id, season_id);

-- Claims are listed newest first, per farmer plot.
CREATE INDEX ix_claims_trigger ON claims (trigger_date, id);
