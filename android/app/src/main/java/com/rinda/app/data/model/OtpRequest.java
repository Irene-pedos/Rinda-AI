package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class OtpRequest {
    @SerializedName("phone")
    private final String phone;

    public OtpRequest(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }
}
