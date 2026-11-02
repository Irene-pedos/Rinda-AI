package com.rinda.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PlotDao {
    @Query("SELECT * FROM plots ORDER BY id DESC")
    LiveData<List<PlotEntity>> getAllPlotsLiveData();

    @Query("SELECT * FROM plots ORDER BY id DESC")
    List<PlotEntity> getAllPlots();

    @Query("SELECT * FROM plots WHERE id = :plotId LIMIT 1")
    PlotEntity getPlotById(long plotId);

    @Query("SELECT * FROM plots WHERE id = :plotId LIMIT 1")
    LiveData<PlotEntity> getPlotByIdLiveData(long plotId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAll(List<PlotEntity> plots);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(PlotEntity plot);

    @Query("DELETE FROM plots WHERE id = :plotId")
    void deleteById(long plotId);

    @Query("DELETE FROM plots")
    void deleteAll();
}
