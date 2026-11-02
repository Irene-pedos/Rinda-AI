package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class RefreshRequest {
    @SerializedName("refreshToken")
    private final String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
