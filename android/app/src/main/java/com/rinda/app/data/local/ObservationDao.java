package com.rinda.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ObservationDao {
    @Query("SELECT * FROM observations WHERE plotId = :plotId ORDER BY date ASC")
    List<ObservationEntity> getObservationsForPlot(long plotId);

    @Query("SELECT * FROM observations WHERE plotId = :plotId ORDER BY date ASC")
    LiveData<List<ObservationEntity>> getObservationsForPlotLiveData(long plotId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ObservationEntity> observations);

    @Query("DELETE FROM observations WHERE plotId = :plotId")
    void deleteByPlotId(long plotId);
}
