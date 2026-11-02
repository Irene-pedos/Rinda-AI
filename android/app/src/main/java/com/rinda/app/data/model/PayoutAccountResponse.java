package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PayoutAccountResponse {
    @SerializedName("provider")
    private String provider; // "MTN_MOMO", "AIRTEL"

    @SerializedName("msisdn")
    private String msisdn;

    @SerializedName("verified")
    private boolean verified;

    @SerializedName("nameMatch")
    private boolean nameMatch;

    public PayoutAccountResponse() {
    }

    public String getProvider() {
        return provider;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isNameMatch() {
        return nameMatch;
    }
}
