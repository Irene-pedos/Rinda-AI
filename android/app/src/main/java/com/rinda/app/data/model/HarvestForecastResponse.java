package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class HarvestForecastResponse {
    @SerializedName("date")
    private String date;

    @SerializedName("lowPct")
    private BigDecimal lowPct;

    @SerializedName("highPct")
    private BigDecimal highPct;

    @SerializedName("confidence")
    private String confidence;

    @SerializedName("modelVersion")
    private String modelVersion;

    public HarvestForecastResponse() {
    }

    public String getDate() {
        return date;
    }

    public BigDecimal getLowPct() {
        return lowPct;
    }

    public BigDecimal getHighPct() {
        return highPct;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }
}
