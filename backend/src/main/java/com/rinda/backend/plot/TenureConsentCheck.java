package com.rinda.backend.plot;

/**
 * How a farmer who is not the registered owner shows the right to farm the plot. The mechanism is undecided
 * (handoff README, open question 6), so it sits behind this interface. Today it is a LEASE or OWNER_CONSENT
 * document that a person reviews.
 */
public interface TenureConsentCheck {

    /** Null when the NON_OWNER flag may be resolved; otherwise why not, shown to the reviewer. */
    String blockReason(long plotId);
}
