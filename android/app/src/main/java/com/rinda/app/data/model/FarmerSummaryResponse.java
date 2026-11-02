package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class FarmerSummaryResponse {
    @SerializedName("farmerId")
    private long farmerId;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("phone")
    private String phone;

    @SerializedName("plotsCount")
    private int plotsCount;

    @SerializedName("pendingVerificationCount")
    private int pendingVerificationCount;

    @SerializedName("atRiskCount")
    private int atRiskCount;

    public FarmerSummaryResponse() {
    }

    public long getFarmerId() {
        return farmerId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public int getPlotsCount() {
        return plotsCount;
    }

    public int getPendingVerificationCount() {
        return pendingVerificationCount;
    }

    public int getAtRiskCount() {
        return atRiskCount;
    }
}
