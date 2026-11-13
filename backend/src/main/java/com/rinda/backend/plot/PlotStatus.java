package com.rinda.backend.plot;

/**
 * Where a plot is in registration and review. Not crop health, and not the verification level.
 * PlotRules derives it from the plot's flags, walk and level; only REJECTED and ARCHIVED are set directly.
 */
public enum PlotStatus {
    /** Registration not finished: no accepted GPS walk or proof document yet. */
    DRAFT,
    /** Registration complete and checks passed; waiting for the agent's field check. */
    PENDING_VERIFICATION,
    /** A check needs a person's review (an open flag). */
    FLAGGED,
    /** Overlaps another plot or shares its UPI. Financial actions are paused until a verifier resolves it. */
    DISPUTED,
    /** Field-verified or higher. */
    ACTIVE,
    REJECTED,
    ARCHIVED
}
