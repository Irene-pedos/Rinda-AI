package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

/** The plot's registration state from the server (docs/API.md, GET /plots/{id}). Not crop health. */
public enum PlotStatus {
    /** Registration not finished: walk the plot and upload the e-title. */
    @SerializedName("DRAFT")
    DRAFT,
    @SerializedName("PENDING_VERIFICATION")
    PENDING_VERIFICATION,
    /** A check needs a person's review (an open flag). */
    @SerializedName("FLAGGED")
    FLAGGED,
    /** Overlaps another plot or shares its UPI. Financial actions are paused. */
    @SerializedName("DISPUTED")
    DISPUTED,
    @SerializedName("ACTIVE")
    ACTIVE,
    @SerializedName("REJECTED")
    REJECTED,
    @SerializedName("ARCHIVED")
    ARCHIVED
}
