package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public enum PlotStatus {
    @SerializedName("PENDING_VERIFICATION")
    PENDING_VERIFICATION,
    @SerializedName("ACTIVE")
    ACTIVE,
    @SerializedName("REJECTED")
    REJECTED,
    @SerializedName("ARCHIVED")
    ARCHIVED
}
