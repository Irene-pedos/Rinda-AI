package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class UserResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("phone")
    private String phone;

    @SerializedName("fullName")
    private String fullName;

    @SerializedName("role")
    private Role role;

    @SerializedName("language")
    private String language;

    @SerializedName("isDemo")
    private boolean isDemo;

    public UserResponse() {
    }

    public UserResponse(long id, String phone, String fullName, Role role, String language, boolean isDemo) {
        this.id = id;
        this.phone = phone;
        this.fullName = fullName;
        this.role = role;
        this.language = language;
        this.isDemo = isDemo;
    }

    public long getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDemo() {
        return isDemo;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
