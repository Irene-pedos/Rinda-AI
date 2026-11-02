package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PayoutAccountRequest {
    @SerializedName("provider")
    private final String provider; // "MTN_MOMO", "AIRTEL"

    @SerializedName("msisdn")
    private final String msisdn;

    public PayoutAccountRequest(String provider, String msisdn) {
        this.provider = provider;
        this.msisdn = msisdn;
    }

    public String getProvider() {
        return provider;
    }

    public String getMsisdn() {
        return msisdn;
    }
}
