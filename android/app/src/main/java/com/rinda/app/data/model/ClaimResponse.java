package com.rinda.app.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class ClaimResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("plotId")
    private long plotId;

    @SerializedName("plotName")
    private String plotName;

    @SerializedName("seasonId")
    private long seasonId;

    @SerializedName("triggerDate")
    private String triggerDate;

    @SerializedName("status")
    private String status; // "UNDER_REVIEW", "FIELD_VISIT_REQUESTED", "APPROVED", "DECLINED", "PAID", "REVIEW_REQUESTED", "FINAL"

    @SerializedName("payoutRate")
    private BigDecimal payoutRate;

    @SerializedName("amount")
    private BigDecimal amount;

    @SerializedName("evidenceSnapshot")
    private JsonElement evidenceSnapshot;

    @SerializedName("decision")
    private ClaimDecisionResponse decision;

    @SerializedName("modelVersion")
    private String modelVersion;

    @SerializedName("isDemo")
    private boolean isDemo;

    @SerializedName("createdAt")
    private String createdAt;

    public ClaimResponse() {
    }

    public long getId() {
        return id;
    }

    public long getPlotId() {
        return plotId;
    }

    public String getPlotName() {
        return plotName != null ? plotName : ("Plot #" + plotId);
    }

    public long getSeasonId() {
        return seasonId;
    }

    public String getTriggerDate() {
        return triggerDate;
    }

    public String getStatus() {
        return status != null ? status : "UNDER_REVIEW";
    }

    public BigDecimal getPayoutRate() {
        return payoutRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public JsonElement getEvidenceSnapshot() {
        return evidenceSnapshot;
    }

    public ClaimDecisionResponse getDecision() {
        return decision;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isDemo() {
        return isDemo;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
