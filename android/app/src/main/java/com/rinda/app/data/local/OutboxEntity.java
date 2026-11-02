package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Local SQLite queue for unsent offline actions (e.g. problem reports, field visits).
 * Processed by WorkManager once an active internet connection is restored.
 */
@Entity(tableName = "outbox")
public class OutboxEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String type; // "PROBLEM_REPORT", "FIELD_VISIT"
    public String payloadJson;
    public String photoPath; // Local file path if image is attached
    public long createdAt;
    public String status; // "PENDING", "SYNCING", "FAILED"
    public int retries;

    public OutboxEntity() {
    }

    @androidx.room.Ignore
    public OutboxEntity(String type, String payloadJson, String photoPath) {
        this.type = type;
        this.payloadJson = payloadJson;
        this.photoPath = photoPath;
        this.createdAt = System.currentTimeMillis();
        this.status = "PENDING";
        this.retries = 0;
    }
}
