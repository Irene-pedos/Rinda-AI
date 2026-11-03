package com.rinda.app.data.model;

import com.google.gson.annotations.SerializedName;

public class NotificationResponse {
    @SerializedName("id")
    private long id;

    @SerializedName("type")
    private String type; // "HEALTH", "CLAIM", "SYSTEM"

    @SerializedName("code")
    private String code; // e.g. "HEALTH_AT_RISK", "HEALTH_WATCH", "HEALTH_RECOVERED"

    @SerializedName("title")
    private String title;

    @SerializedName("body")
    private String body;

    @SerializedName("plotId")
    private Long plotId;

    @SerializedName("plotName")
    private String plotName;

    @SerializedName("link")
    private String link;

    @SerializedName("isRead")
    private boolean isRead;

    @SerializedName("isDemo")
    private boolean isDemo;

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

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getPlotId() {
        return plotId;
    }

    public String getPlotName() {
        return plotName;
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

    public boolean isDemo() {
        return isDemo;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
