package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.rinda.app.data.model.GeoJsonPolygon;
import com.rinda.app.data.model.PlotStatus;
import java.math.BigDecimal;

/**
 * Local SQLite cache for farmer plots.
 * Single source of truth is always the backend MySQL server.
 */
@Entity(tableName = "plots")
public class PlotEntity {
    @PrimaryKey
    public long id;
    public long farmerId;
    public String name;
    public String crop;
    public GeoJsonPolygon boundary;
    public BigDecimal areaHa;
    public String plantingDate;
    public PlotStatus status;
    public String dataStatus;
    public boolean isDemo;
    public String createdAt;
    public String updatedAt;
    public long cachedAt;

    public PlotEntity() {
    }
}
