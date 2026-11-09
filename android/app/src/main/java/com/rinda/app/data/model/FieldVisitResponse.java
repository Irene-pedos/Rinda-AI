package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

/**
 * Field visit recorded by a cooperative agent (ground truth observation).
 * Mirrors backend AgentDtos.FieldVisitResponse.
 */
public class FieldVisitResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("plotId")
    private long plotId;

    @SerializedName("plotName")
    private String plotName;

    @SerializedName("agentId")
    private long agentId;

    @SerializedName("plantCondition")
    private String plantCondition;

    @SerializedName("observedCause")
    private String observedCause;

    @SerializedName("growthStage")
    private String growthStage;

    @SerializedName("notes")
    private String notes;

    @SerializedName("lat")
    private BigDecimal lat;

    @SerializedName("lng")
    private BigDecimal lng;

    @SerializedName("visitedAt")
    private String visitedAt;

    @SerializedName("claimId")
    private Long claimId;

    @SerializedName("photoUrl")
    private String photoUrl;

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

    public long getAgentId() { return agentId; }
    public void setAgentId(long agentId) { this.agentId = agentId; }

    public String getPlantCondition() { return plantCondition; }
    public void setPlantCondition(String plantCondition) { this.plantCondition = plantCondition; }

    public String getObservedCause() { return observedCause; }
    public void setObservedCause(String observedCause) { this.observedCause = observedCause; }

    public String getGrowthStage() { return growthStage; }
    public void setGrowthStage(String growthStage) { this.growthStage = growthStage; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getLat() { return lat; }
    public void setLat(BigDecimal lat) { this.lat = lat; }

    public BigDecimal getLng() { return lng; }
    public void setLng(BigDecimal lng) { this.lng = lng; }

    public String getVisitedAt() { return visitedAt; }
    public void setVisitedAt(String visitedAt) { this.visitedAt = visitedAt; }

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isDemo() { return isDemo; }
    public void setDemo(boolean demo) { isDemo = demo; }
}
