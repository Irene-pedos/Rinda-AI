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

    public PlotResponse() {
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
