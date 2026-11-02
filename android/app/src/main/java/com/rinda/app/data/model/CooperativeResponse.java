package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class CooperativeResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("district")
    private String district;

    @SerializedName("sector")
    private String sector;

    @SerializedName("isDemo")
    private boolean isDemo;

    public CooperativeResponse() {
    }

    public CooperativeResponse(long id, String name, String district, String sector, boolean isDemo) {
        this.id = id;
        this.name = name;
        this.district = district;
        this.sector = sector;
        this.isDemo = isDemo;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDistrict() {
        return district;
    }

    public String getSector() {
        return sector;
    }

    public boolean isDemo() {
        return isDemo;
    }

    @Override
    public String toString() {
        return name;
    }
}
