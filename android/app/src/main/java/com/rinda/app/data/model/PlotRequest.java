package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PlotRequest {
    @SerializedName("name")
    private final String name;

    @SerializedName("boundary")
    private final GeoJsonPolygon boundary;

    @SerializedName("plantingDate")
    private final String plantingDate; // "yyyy-MM-dd"

    @SerializedName("crop")
    private final String crop; // Optional; if sent must be MAIZE

    public PlotRequest(String name, GeoJsonPolygon boundary, String plantingDate) {
        this(name, boundary, plantingDate, "MAIZE");
    }

    public PlotRequest(String name, GeoJsonPolygon boundary, String plantingDate, String crop) {
        this.name = name;
        this.boundary = boundary;
        this.plantingDate = plantingDate;
        this.crop = crop;
    }

    public String getName() {
        return name;
    }

    public GeoJsonPolygon getBoundary() {
        return boundary;
    }

    public String getPlantingDate() {
        return plantingDate;
    }

    public String getCrop() {
        return crop;
    }
}
