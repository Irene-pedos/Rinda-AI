package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local SQLite cache for notifications / alerts.
 */
@Entity(tableName = "notifications")
public class NotificationEntity {
    @PrimaryKey
    public long id;
    public String type; // HEALTH, CLAIM, SYSTEM
    public String code; // HEALTH_AT_RISK, HEALTH_WATCH, etc.
    public String title;
    public String body;
    public Long plotId;
    public String plotName;
    public String link;
    public boolean isRead;
    public boolean isDemo;
    public String createdAt;
    public long cachedAt;

    public NotificationEntity() {
    }
}
