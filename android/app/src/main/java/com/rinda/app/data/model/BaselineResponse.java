package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

public class BaselineResponse {
    @SerializedName("seasonType")
    private String seasonType;

    @SerializedName("version")
    private String version;

    @SerializedName("binDays")
    private Integer binDays;

    @SerializedName("dataOrigin")
    private DataOrigin dataOrigin;

    @SerializedName("bins")
    private List<BaselineBinResponse> bins;

    public BaselineResponse() {
    }

    public String getSeasonType() {
        return seasonType;
    }

    public String getVersion() {
        return version;
    }

    public Integer getBinDays() {
        return binDays != null ? binDays : 10;
    }

    public DataOrigin getDataOrigin() {
        return dataOrigin;
    }

    public List<BaselineBinResponse> getBins() {
        return bins != null ? bins : Collections.emptyList();
    }
}
