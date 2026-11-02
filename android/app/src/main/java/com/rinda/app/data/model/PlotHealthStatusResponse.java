package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class PlotHealthStatusResponse {
    @SerializedName("date")
    private String date;

    @SerializedName("status")
    private String status; // "HEALTHY", "WATCH", "AT_RISK", "NOT_ENOUGH_DATA"

    @SerializedName("anomaly")
    private BigDecimal anomaly;

    @SerializedName("rainPct")
    private BigDecimal rainPct;

    @SerializedName("cause")
    private String cause; // e.g. "WATER_DEFICIT", "UNEXPLAINED"

    @SerializedName("confidence")
    private String confidence; // "HIGH", "MEDIUM", "LOW"

    @SerializedName("explanation")
    private String explanation;

    @SerializedName("modelVersion")
    private String modelVersion;

    public PlotHealthStatusResponse() {
    }

    public String getDate() {
        return date;
    }

    public String getStatus() {
        return status != null ? status : "NOT_ENOUGH_DATA";
    }

    public BigDecimal getAnomaly() {
        return anomaly;
    }

    public BigDecimal getRainPct() {
        return rainPct;
    }

    public String getCause() {
        return cause;
    }

    public String getConfidence() {
        return confidence != null ? confidence : "LOW";
    }

    public String getExplanation() {
        return explanation;
    }

    public String getModelVersion() {
        return modelVersion;
    }
}
