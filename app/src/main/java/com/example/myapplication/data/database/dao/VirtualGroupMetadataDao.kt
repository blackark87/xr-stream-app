package com.example.myapplication.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.myapplication.data.database.entity.VirtualGroupMetadata

@Dao
interface VirtualGroupMetadataDao {
    @Query("SELECT * FROM virtual_group_metadata WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): VirtualGroupMetadata?

    @Query("SELECT * FROM virtual_group_metadata WHERE code = :code AND isMiss = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestHitByCode(code: String): VirtualGroupMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: VirtualGroupMetadata)

    @Query("DELETE FROM virtual_group_metadata WHERE cacheKey = :cacheKey")
    suspend fun deleteByCacheKey(cacheKey: String)
}