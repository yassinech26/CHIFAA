package com.example.chifaaapp.directory;
import androidx.room.*;
import java.util.List;

@Dao
public interface OrganisationDao {
    @Query("SELECT * FROM directory_cache ORDER BY id") List<OrganisationCacheEntity> all();
    @Query("SELECT * FROM directory_cache WHERE id=:id") OrganisationCacheEntity find(String id);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void insert(List<OrganisationCacheEntity> rows);
    @Query("DELETE FROM directory_cache") void clear();

    @Transaction
    default void replace(List<OrganisationCacheEntity> rows) {
        clear();
        insert(rows);
    }
}
