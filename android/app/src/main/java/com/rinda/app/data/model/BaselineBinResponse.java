package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class BaselineBinResponse {
    @SerializedName("dapBin")
    private int dapBin;

    @SerializedName("p10")
    private BigDecimal p10;

    @SerializedName("p50")
    private BigDecimal p50;

    @SerializedName("p90")
    private BigDecimal p90;

    public BaselineBinResponse() {
    }

    public int getDapBin() {
        return dapBin;
    }

    public BigDecimal getP10() {
        return p10;
    }

    public BigDecimal getP50() {
        return p50;
    }

    public BigDecimal getP90() {
        return p90;
    }
}
