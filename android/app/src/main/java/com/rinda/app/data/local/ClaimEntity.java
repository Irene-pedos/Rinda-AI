package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.math.BigDecimal;

/**
 * Local SQLite cache for insurance claims.
 * Status always strictly mirrors the server; local app never invents claims or mutates payout status.
 */
@Entity(tableName = "claims")
public class ClaimEntity {
    @PrimaryKey
    public long id;
    public long plotId;
    public String plotName;
    public long seasonId;
    public String triggerDate;
    public String status;
    public BigDecimal payoutRate;
    public BigDecimal amount;
    public String evidenceSnapshotJson;
    public String officerDecision;
    public String officerReason;
    public String modelVersion;
    public boolean isDemo;
    public String createdAt;
    public long cachedAt;

    public ClaimEntity() {
    }
}
