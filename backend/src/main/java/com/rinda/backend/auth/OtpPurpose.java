package com.rinda.backend.auth;

/** What a one-time code is for. A code only works for the purpose it was sent for. */
public enum OtpPurpose {
    LOGIN,
    /** Verifying a mobile-money number for simulated payouts (spec §2.2 #12). */
    PAYOUT
}
