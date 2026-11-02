package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class ClaimDecisionResponse {
    @SerializedName("decision")
    private String decision; // "APPROVED", "DECLINED"

    @SerializedName("reason")
    private String reason;

    @SerializedName("decidedAt")
    private String decidedAt;

    public ClaimDecisionResponse() {
    }

    public String getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public String getDecidedAt() {
        return decidedAt;
    }
}
