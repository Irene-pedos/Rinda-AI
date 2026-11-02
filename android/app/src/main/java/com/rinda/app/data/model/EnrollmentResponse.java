package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.math.BigDecimal;

public class EnrollmentResponse {
    @SerializedName("status")
    private String status; // "PENDING", "COVERED", "CANCELLED"

    @SerializedName("seasonCode")
    private String seasonCode;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("sumInsured")
    private BigDecimal sumInsured;

    @SerializedName("premium")
    private BigDecimal premium;

    @SerializedName("isDemo")
    private boolean isDemo;

    public EnrollmentResponse() {
    }

    public String getStatus() {
        return status != null ? status : "PENDING";
    }

    public String getSeasonCode() {
        return seasonCode;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public boolean isDemo() {
        return isDemo;
    }
}
