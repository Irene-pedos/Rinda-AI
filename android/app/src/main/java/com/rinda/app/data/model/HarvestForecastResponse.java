package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class HarvestForecastResponse {
    @SerializedName("available")
    private boolean available;

    @SerializedName("date")
    private String date;

    @SerializedName("lowPct")
    private BigDecimal lowPct;

    @SerializedName("highPct")
    private BigDecimal highPct;

    @SerializedName("confidence")
    private String confidence;

    @SerializedName("reason")
    private String reason; // NOT_ANALYSED, TOO_EARLY, NOT_ENOUGH_DATA

    @SerializedName("dataQualityNote")
    private String dataQualityNote;

    @SerializedName("modelVersion")
    private String modelVersion;

    @SerializedName("dataOrigin")
    private DataOrigin dataOrigin;

    public HarvestForecastResponse() {
    }

    public boolean isAvailable() {
        return available;
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

    public String getReason() {
        return reason;
    }

    public String getDataQualityNote() {
        return dataQualityNote;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public DataOrigin getDataOrigin() {
        return dataOrigin;
    }
}
