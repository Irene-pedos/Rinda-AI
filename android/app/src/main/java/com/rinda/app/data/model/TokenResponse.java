package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class TokenResponse {
    @SerializedName("accessToken")
    private String accessToken;

    @SerializedName("accessTokenExpiresIn")
    private long accessTokenExpiresIn;

    @SerializedName("refreshToken")
    private String refreshToken;

    @SerializedName("user")
    private UserResponse user;

    public TokenResponse() {
    }

    public TokenResponse(String accessToken, long accessTokenExpiresIn, String refreshToken, UserResponse user) {
        this.accessToken = accessToken;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public UserResponse getUser() {
        return user;
    }
}
