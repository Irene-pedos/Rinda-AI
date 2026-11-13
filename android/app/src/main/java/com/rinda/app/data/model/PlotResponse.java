package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class PlotResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("farmerId")
    private long farmerId;

    @SerializedName("name")
    private String name;

    @SerializedName("crop")
    private String crop;

    @SerializedName("boundary")
    private GeoJsonPolygon boundary;

    @SerializedName("areaHa")
    private BigDecimal areaHa;

    @SerializedName("plantingDate")
    private String plantingDate; // "yyyy-MM-dd", nullable on seeded demo plots

    @SerializedName("status")
    private PlotStatus status;

    @SerializedName("dataStatus")
    private String dataStatus; // "CONFIRMED" or "TO_CONFIRM"

    @SerializedName("isDemo")
    private boolean isDemo;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    // Registration and verification (docs/API.md, GET /plots/{id})
    @SerializedName("upi")
    private String upi;

    /** SELF_DECLARED, DOCUMENT_SUPPORTED, FIELD_VERIFIED or NLA_VERIFIED. Always shown next to the plot. */
    @SerializedName("verificationLevel")
    private String verificationLevel;

    @SerializedName("walkedAreaM2")
    private BigDecimal walkedAreaM2;

    @SerializedName("documentAreaM2")
    private BigDecimal documentAreaM2;

    @SerializedName("relationshipToPlot")
    private String relationshipToPlot;

    @SerializedName("financialActionsAllowed")
    private boolean financialActionsAllowed;

    @SerializedName("flags")
    private java.util.List<PlotRegistration.Flag> flags;

    @SerializedName("checks")
    private java.util.List<PlotRegistration.Check> checks;

    public PlotResponse() {
    }

    public String getUpi() {
        return upi;
    }

    public String getVerificationLevel() {
        return verificationLevel;
    }

    public BigDecimal getWalkedAreaM2() {
        return walkedAreaM2;
    }

    public BigDecimal getDocumentAreaM2() {
        return documentAreaM2;
    }

    public String getRelationshipToPlot() {
        return relationshipToPlot;
    }

    public boolean isFinancialActionsAllowed() {
        return financialActionsAllowed;
    }

    public java.util.List<PlotRegistration.Flag> getFlags() {
        return flags != null ? flags : java.util.Collections.emptyList();
    }

    public java.util.List<PlotRegistration.Check> getChecks() {
        return checks != null ? checks : java.util.Collections.emptyList();
    }

    /** True once a check of this name has run (PASSED, FLAGGED or REVIEWED). */
    public boolean checkRan(String name) {
        for (PlotRegistration.Check c : getChecks()) {
            if (name.equals(c.check)) {
                return !"PENDING".equals(c.result) && !"NOT_RUN".equals(c.result);
            }
        }
        return false;
    }

    public long getId() {
        return id;
    }

    public long getFarmerId() {
        return farmerId;
    }

    public String getName() {
        return name;
    }

    public String getCrop() {
        return crop;
    }

    public GeoJsonPolygon getBoundary() {
        return boundary;
    }

    public BigDecimal getAreaHa() {
        return areaHa;
    }

    public String getPlantingDate() {
        return plantingDate;
    }

    public PlotStatus getStatus() {
        return status;
    }

    public String getDataStatus() {
        return dataStatus;
    }

    public boolean isDemo() {
        return isDemo;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(PlotStatus status) {
        this.status = status;
    }
}
