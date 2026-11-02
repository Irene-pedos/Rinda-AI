package com.rinda.app.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY createdAt ASC")
    List<OutboxEntity> getPendingItems();

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    LiveData<Integer> getPendingCountLiveData();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(OutboxEntity item);

    @Update
    void update(OutboxEntity item);

    @Query("DELETE FROM outbox WHERE id = :id")
    void deleteById(long id);
}
