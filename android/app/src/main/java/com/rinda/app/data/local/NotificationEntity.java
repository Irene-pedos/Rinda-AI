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
    public String title;
    public String body;
    public String link;
    public boolean isRead;
    public String createdAt;
    public long cachedAt;

    public NotificationEntity() {
    }
}
