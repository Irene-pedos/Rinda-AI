package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class NotificationResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("type")
    private String type; // "HEALTH", "CLAIM", "SYSTEM"

    @SerializedName("title")
    private String title;

    @SerializedName("body")
    private String body;

    @SerializedName("link")
    private String link;

    @SerializedName("isRead")
    private boolean isRead;

    @SerializedName("createdAt")
    private String createdAt;

    public NotificationResponse() {
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return type != null ? type : "SYSTEM";
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
