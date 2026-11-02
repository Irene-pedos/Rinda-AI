package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class ObservationResponse {
    @SerializedName("date")
    private String date; // "yyyy-MM-dd"

    @SerializedName("dap")
    private Integer dap; // Days after planting, nullable if planting date unknown

    @SerializedName("source")
    private String source; // "S2", "S1", "S1_ESTIMATE"

    @SerializedName("ndvi")
    private BigDecimal ndvi; // Null when too few clear pixels

    @SerializedName("smoothedNdvi")
    private BigDecimal smoothedNdvi;

    @SerializedName("clearPixels")
    private Integer clearPixels;

    @SerializedName("cloudPct")
    private BigDecimal cloudPct;

    @SerializedName("dataOrigin")
    private DataOrigin dataOrigin;

    public ObservationResponse() {
    }

    public String getDate() {
        return date;
    }

    public Integer getDap() {
        return dap;
    }

    public String getSource() {
        return source;
    }

    public BigDecimal getNdvi() {
        return ndvi;
    }

    public BigDecimal getSmoothedNdvi() {
        return smoothedNdvi;
    }

    public Integer getClearPixels() {
        return clearPixels;
    }

    public BigDecimal getCloudPct() {
        return cloudPct;
    }

    public DataOrigin getDataOrigin() {
        return dataOrigin;
    }
}
