package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PlotVerifyRequest {
    @SerializedName("decision")
    private final String decision; // "ACTIVE", "REJECTED"

    @SerializedName("confirmedPlantingDate")
    private final String confirmedPlantingDate; // "yyyy-MM-dd"

    @SerializedName("rejectionReason")
    private final String rejectionReason;

    public PlotVerifyRequest(String decision, String confirmedPlantingDate, String rejectionReason) {
        this.decision = decision;
        this.confirmedPlantingDate = confirmedPlantingDate;
        this.rejectionReason = rejectionReason;
    }

    public String getDecision() {
        return decision;
    }

    public String getConfirmedPlantingDate() {
        return confirmedPlantingDate;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
