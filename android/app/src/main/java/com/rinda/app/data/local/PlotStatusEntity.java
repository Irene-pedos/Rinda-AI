package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.math.BigDecimal;

/**
 * Local SQLite cache for crop health status per plot.
 */
@Entity(tableName = "plot_status")
public class PlotStatusEntity {
    @PrimaryKey
    public long plotId;
    public String date;
    public String status; // HEALTHY, WATCH, AT_RISK, NOT_ENOUGH_DATA
    public BigDecimal anomaly;
    public BigDecimal rainPct;
    public String cause;
    public String confidence;
    public boolean needsFieldVisit;
    public String dataQualityNote;
    public String modelVersion;
    public String dataOrigin;
    public long cachedAt;

    public PlotStatusEntity() {
    }
}
