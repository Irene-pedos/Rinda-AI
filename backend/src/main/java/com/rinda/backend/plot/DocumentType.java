package com.rinda.backend.plot;

public enum DocumentType {
    E_TITLE, PORTAL_SCREENSHOT, LEASE, OWNER_CONSENT;

    /** Documents that show the plot itself (UPI, area). The other two show the right to farm it. */
    public boolean isPlotProof() {
        return this == E_TITLE || this == PORTAL_SCREENSHOT;
    }
}
