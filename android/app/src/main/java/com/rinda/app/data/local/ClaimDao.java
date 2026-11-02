package com.rinda.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface ClaimDao {
    @Query("SELECT * FROM claims ORDER BY triggerDate DESC, id DESC")
    LiveData<List<ClaimEntity>> getAllClaimsLiveData();

    @Query("SELECT * FROM claims WHERE id = :id LIMIT 1")
    LiveData<ClaimEntity> getClaimByIdLiveData(long id);

    @Query("SELECT * FROM claims WHERE id = :id LIMIT 1")
    ClaimEntity getClaimById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ClaimEntity> claims);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(ClaimEntity claim);

    @Query("DELETE FROM claims")
    void deleteAll();
}
