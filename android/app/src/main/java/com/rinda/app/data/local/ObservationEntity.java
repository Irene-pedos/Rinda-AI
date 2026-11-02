package com.rinda.app.data.local;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.rinda.app.data.model.DataOrigin;
import java.math.BigDecimal;

/**
 * Local SQLite cache for NDVI observations.
 */
@Entity(tableName = "observations", indices = {@Index(value = {"plotId", "date", "source"}, unique = true)})
public class ObservationEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long plotId;
    public String date;
    public Integer dap;
    public String source;
    public BigDecimal ndvi;
    public BigDecimal smoothedNdvi;
    public Integer clearPixels;
    public BigDecimal cloudPct;
    public DataOrigin dataOrigin;
    public long cachedAt;

    public ObservationEntity() {
    }
}
