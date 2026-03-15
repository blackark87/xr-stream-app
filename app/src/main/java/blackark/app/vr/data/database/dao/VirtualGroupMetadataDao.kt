package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import blackark.app.vr.data.database.entity.JvrPerformer
import blackark.app.vr.data.database.entity.VirtualGroupMetadata
import blackark.app.vr.data.database.entity.VirtualGroupMetadataGenre
import blackark.app.vr.data.database.entity.VirtualGroupMetadataPerformerCrossRef
import blackark.app.vr.data.database.entity.VirtualGroupMetadataRecord

@Dao
interface VirtualGroupMetadataDao {
    @Transaction
    @Query("SELECT * FROM virtual_group_metadata WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByCacheKey(cacheKey: String): VirtualGroupMetadataRecord?

    @Transaction
    @Query("SELECT * FROM virtual_group_metadata WHERE cacheKey IN (:cacheKeys)")
    suspend fun getByCacheKeys(cacheKeys: List<String>): List<VirtualGroupMetadataRecord>

    @Transaction
    @Query("SELECT * FROM virtual_group_metadata WHERE code = :code AND isMiss = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestHitByCode(code: String): VirtualGroupMetadataRecord?

    @Upsert
    suspend fun upsertMetadata(metadata: VirtualGroupMetadata)

    @Upsert
    suspend fun upsertGenres(genres: List<VirtualGroupMetadataGenre>)

    @Upsert
    suspend fun upsertPerformers(performers: List<JvrPerformer>)

    @Upsert
    suspend fun upsertPerformerRefs(refs: List<VirtualGroupMetadataPerformerCrossRef>)

    @Query("SELECT * FROM jvr_performers WHERE performerId IN (:performerIds)")
    suspend fun getPerformersByIds(performerIds: List<String>): List<JvrPerformer>

    @Query("DELETE FROM virtual_group_metadata_genres WHERE cacheKey = :cacheKey")
    suspend fun deleteGenresByCacheKey(cacheKey: String)

    @Query("DELETE FROM virtual_group_metadata_performers WHERE cacheKey = :cacheKey")
    suspend fun deletePerformerRefsByCacheKey(cacheKey: String)

    @Transaction
    suspend fun replace(record: VirtualGroupMetadataRecord) {
        upsertMetadata(record.metadata)
        deleteGenresByCacheKey(record.metadata.cacheKey)
        deletePerformerRefsByCacheKey(record.metadata.cacheKey)
        if (record.genres.isNotEmpty()) {
            upsertGenres(record.genres)
        }
        if (record.performers.isNotEmpty()) {
            val existingPerformers = getPerformersByIds(record.performers.map { it.performerId })
                .associateBy { it.performerId }
            upsertPerformers(
                record.performers.map { incoming ->
                    mergePerformer(
                        existing = existingPerformers[incoming.performerId],
                        incoming = incoming,
                    )
                }
            )
        }
        if (record.performerRefs.isNotEmpty()) {
            upsertPerformerRefs(record.performerRefs)
        }
    }

    @Query("DELETE FROM virtual_group_metadata WHERE cacheKey = :cacheKey")
    suspend fun deleteByCacheKey(cacheKey: String)

    @Query("DELETE FROM virtual_group_metadata")
    suspend fun clearAll()

    private fun mergePerformer(
        existing: JvrPerformer?,
        incoming: JvrPerformer,
    ): JvrPerformer {
        if (existing == null) return incoming

        val incomingHasDistinctEnglishName =
            incoming.englishName.isNotBlank() && incoming.englishName != incoming.japaneseName

        return incoming.copy(
            englishName = when {
                incomingHasDistinctEnglishName -> incoming.englishName
                existing.englishName.isNotBlank() -> existing.englishName
                else -> incoming.englishName
            },
            japaneseName = incoming.japaneseName ?: existing.japaneseName,
            remoteProfileImageUrl = incoming.remoteProfileImageUrl
                ?: existing.remoteProfileImageUrl,
            localProfileImageUrl = incoming.localProfileImageUrl ?: existing.localProfileImageUrl,
            updatedAt = maxOf(existing.updatedAt, incoming.updatedAt),
        )
    }
}
