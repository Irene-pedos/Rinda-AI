package com.rinda.backend.plot;

/** How far Rinda trusts a plot (docs/01-plot-verification.md). Declared in increasing order of trust. */
public enum VerificationLevel {
    /** UPI typed by the farmer only. Demo use only. */
    SELF_DECLARED,
    /** UPI, uploaded e-title or portal screenshot, GPS walk, automatic checks passed. Onboarding only. */
    DOCUMENT_SUPPORTED,
    /** The above, plus a person's check at the plot with a geotagged photo. The minimum for payouts. */
    FIELD_VERIFIED,
    /** Matched against NLA parcel data. Not reachable until an NLA provider exists. */
    NLA_VERIFIED;

    public boolean atLeast(VerificationLevel other) {
        return compareTo(other) >= 0;
    }
}
