package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class OtpVerifyRequest {
    @SerializedName("phone")
    private final String phone;

    @SerializedName("code")
    private final String code;

    @SerializedName("fullName")
    private final String fullName;

    @SerializedName("cooperativeId")
    private final Long cooperativeId;

    public OtpVerifyRequest(String phone, String code) {
        this(phone, code, null, null);
    }

    public OtpVerifyRequest(String phone, String code, String fullName, Long cooperativeId) {
        this.phone = phone;
        this.code = code;
        this.fullName = fullName;
        this.cooperativeId = cooperativeId;
    }

    public String getPhone() {
        return phone;
    }

    public String getCode() {
        return code;
    }

    public String getFullName() {
        return fullName;
    }

    public Long getCooperativeId() {
        return cooperativeId;
    }
}
