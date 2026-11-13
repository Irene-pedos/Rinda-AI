package com.rinda.backend.plot;

public enum FlagType {
    /** Walked location outside the admin area in the UPI. Never raised while that check is switched off. */
    OUTSIDE_ADMIN_AREA,
    AREA_MISMATCH,
    /** The area tolerance is not supplied yet, so a person compares the areas. */
    AREA_UNCHECKED,
    /** The GPS accuracy limit is not supplied yet, so a person reviews the walk. */
    GPS_ACCURACY_UNCHECKED,
    MOCK_LOCATION,
    OVERLAP,
    DUPLICATE_UPI,
    /** Not the registered owner: needs a lease or the owner's consent, reviewed by a person. */
    NON_OWNER,
    /** Drawn on a map before GPS walks. Resolved by walking the plot. */
    LEGACY_DRAWN_BOUNDARY,
    SATELLITE_INCONSISTENT,
    OTHER;

    /** Disputes make a plot DISPUTED, which pauses every financial action. */
    public boolean isDispute() {
        return this == OVERLAP || this == DUPLICATE_UPI;
    }
}
