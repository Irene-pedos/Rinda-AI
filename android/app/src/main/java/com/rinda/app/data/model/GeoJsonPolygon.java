package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GeoJsonPolygon {
    @SerializedName("type")
    private String type = "Polygon";

    @SerializedName("coordinates")
    private List<List<List<Double>>> coordinates;

    public GeoJsonPolygon() {
    }

    public GeoJsonPolygon(List<List<List<Double>>> coordinates) {
        this.type = "Polygon";
        this.coordinates = coordinates;
    }

    public String getType() {
        return type;
    }

    public List<List<List<Double>>> getCoordinates() {
        return coordinates;
    }
}
