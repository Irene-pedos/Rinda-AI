package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Problem report created by a farmer (evidence only, never creates claims directly).
 * Mirrors backend ProblemReportDtos.ProblemReportResponse.
 */
public class ProblemReportResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("plotId")
    private long plotId;

    @SerializedName("plotName")
    private String plotName;

    @SerializedName("farmerId")
    private long farmerId;

    @SerializedName("reason")
    private String reason;

    @SerializedName("note")
    private String note;

    @SerializedName("agentVisitRequested")
    private boolean agentVisitRequested;

    @SerializedName("claimId")
    private Long claimId;

    @SerializedName("photoUrl")
    private String photoUrl;

    @SerializedName("reportedAt")
    private String reportedAt;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("isDemo")
    private boolean isDemo;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPlotId() { return plotId; }
    public void setPlotId(long plotId) { this.plotId = plotId; }

    public String getPlotName() { return plotName; }
    public void setPlotName(String plotName) { this.plotName = plotName; }

    public long getFarmerId() { return farmerId; }
    public void setFarmerId(long farmerId) { this.farmerId = farmerId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isAgentVisitRequested() { return agentVisitRequested; }
    public void setAgentVisitRequested(boolean agentVisitRequested) { this.agentVisitRequested = agentVisitRequested; }

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getReportedAt() { return reportedAt; }
    public void setReportedAt(String reportedAt) { this.reportedAt = reportedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isDemo() { return isDemo; }
    public void setDemo(boolean demo) { isDemo = demo; }
}
