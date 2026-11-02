package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class PayoutVerifyRequest {
    @SerializedName("code")
    private final String code;

    public PayoutVerifyRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
