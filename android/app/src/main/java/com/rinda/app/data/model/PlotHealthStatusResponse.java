package com.rinda.app.data.model;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class PlotHealthStatusResponse {
    @SerializedName("date")
    private String date; // "yyyy-MM-dd"

    @SerializedName("status")
    private String status; // "HEALTHY", "WATCH", "AT_RISK", "NOT_ENOUGH_DATA"

    @SerializedName("anomaly")
    private BigDecimal anomaly;

    @SerializedName("rainPct")
    private BigDecimal rainPct;

    @SerializedName("cause")
    private String cause; // "WATER_DEFICIT", "UNEXPLAINED"

    @SerializedName("confidence")
    private String confidence; // "HIGH", "MEDIUM", "LOW"

    @SerializedName("needsFieldVisit")
    private boolean needsFieldVisit;

    @SerializedName("signals")
    private JsonElement signals;

    @SerializedName("dataQualityNote")
    private String dataQualityNote;

    @SerializedName("modelVersion")
    private String modelVersion;

    @SerializedName("dataOrigin")
    private DataOrigin dataOrigin;

    @SerializedName("analysedAt")
    private String analysedAt;

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

    public boolean isNeedsFieldVisit() {
        return needsFieldVisit;
    }

    public JsonElement getSignals() {
        return signals;
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

    public String getAnalysedAt() {
        return analysedAt;
    }

    public String getFriendlyCause() {
        if ("WATER_DEFICIT".equalsIgnoreCase(cause)) {
            return "Water Deficit (Drought)";
        } else if ("UNEXPLAINED".equalsIgnoreCase(cause)) {
            return "Unexplained Stress (Field visit recommended)";
        } else if (cause != null && !cause.isEmpty()) {
            return cause;
        }
        return "Normal Growth";
    }
}
