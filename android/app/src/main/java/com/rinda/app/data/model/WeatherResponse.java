package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class WeatherResponse {
    @SerializedName("date")
    private String date; // "yyyy-MM-dd"

    @SerializedName("dap")
    private Integer dap;

    @SerializedName("rainMm")
    private BigDecimal rainMm;

    @SerializedName("dataOrigin")
    private DataOrigin dataOrigin;

    public WeatherResponse() {
    }

    public String getDate() {
        return date;
    }

    public Integer getDap() {
        return dap;
    }

    public BigDecimal getRainMm() {
        return rainMm;
    }

    public DataOrigin getDataOrigin() {
        return dataOrigin;
    }
}
