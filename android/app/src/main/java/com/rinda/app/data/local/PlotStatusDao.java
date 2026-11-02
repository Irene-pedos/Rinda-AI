package com.rinda.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface PlotStatusDao {
    @Query("SELECT * FROM plot_status WHERE plotId = :plotId LIMIT 1")
    PlotStatusEntity getStatusByPlotId(long plotId);

    @Query("SELECT * FROM plot_status WHERE plotId = :plotId LIMIT 1")
    LiveData<PlotStatusEntity> getStatusByPlotIdLiveData(long plotId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PlotStatusEntity status);

    @Query("DELETE FROM plot_status WHERE plotId = :plotId")
    void deleteByPlotId(long plotId);
}
