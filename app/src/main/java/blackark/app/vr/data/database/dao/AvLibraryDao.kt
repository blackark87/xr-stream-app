package blackark.app.vr.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset

@Dao
interface AvLibraryDao {
    @Upsert
    suspend fun upsertAsset(asset: AvLibraryAsset)

    @Upsert
    suspend fun upsertLocation(location: AvAssetLocation)

    @Upsert
    suspend fun upsertLocations(locations: List<AvAssetLocation>)

    @Query("SELECT * FROM av_library_assets WHERE assetKey = :assetKey LIMIT 1")
    suspend fun getAssetByKey(assetKey: String): AvLibraryAsset?

    @Query(
        """
        SELECT * FROM av_library_assets
        WHERE sourceScope = :sourceScope AND normalizedCode = :normalizedCode
        LIMIT 1
        """
    )
    suspend fun getAssetBySourceAndCode(
        sourceScope: String,
        normalizedCode: String
    ): AvLibraryAsset?

    @Query("SELECT * FROM av_asset_locations WHERE filePath = :filePath LIMIT 1")
    suspend fun getLocationByPath(filePath: String): AvAssetLocation?

    @Query(
        """
        SELECT * FROM av_asset_locations
        WHERE sourceScope = :sourceScope
          AND size = :size
          AND isPresent = 0
          AND contentFingerprint IS NOT NULL
        ORDER BY lastSeenAt DESC
        """
    )
    suspend fun getMissingFingerprintCandidates(
        sourceScope: String,
        size: Long
    ): List<AvAssetLocation>

    @Query(
        """
        SELECT * FROM av_asset_locations
        WHERE assetKey = :assetKey
          AND size = :size
          AND isPresent = 0
          AND (
            (:partNumber IS NULL AND partNumber IS NULL) OR
            partNumber = :partNumber
          )
        ORDER BY lastSeenAt DESC
        LIMIT 1
        """
    )
    suspend fun getMissingFallbackLocation(
        assetKey: String,
        partNumber: Int?,
        size: Long,
    ): AvAssetLocation?

    @Query(
        """
        SELECT * FROM av_library_assets
        WHERE sourceScope = :sourceScope
          AND assetKey IN (
            SELECT DISTINCT assetKey FROM av_asset_locations
            WHERE sourceScope = :sourceScope AND isPresent = 1
          )
        ORDER BY lastSeenAt DESC, normalizedCode ASC
        """
    )
    suspend fun getPresentAssetsBySource(sourceScope: String): List<AvLibraryAsset>

    @Query(
        """
        SELECT * FROM av_library_assets
        WHERE sourceScope = :sourceScope
          AND hasMetadata = 1
          AND metadataCacheKey IS NOT NULL
          AND metadataCacheKey IN (
            SELECT m.cacheKey
            FROM virtual_group_metadata AS m
            LEFT JOIN virtual_group_metadata_performers AS p
              ON p.cacheKey = m.cacheKey
            WHERE m.isMiss = 0
            GROUP BY m.cacheKey
            HAVING COUNT(p.performerId) = 0
          )
        ORDER BY lastSeenAt DESC, normalizedCode ASC
        """
    )
    suspend fun getPresentAssetsMissingPerformerRefs(sourceScope: String): List<AvLibraryAsset>

    @Query(
        """
        SELECT * FROM av_asset_locations
        WHERE sourceScope = :sourceScope AND isPresent = 1
        ORDER BY assetKey ASC, partNumber ASC, fileName ASC
        """
    )
    suspend fun getPresentLocationsBySource(sourceScope: String): List<AvAssetLocation>

    @Query(
        """
        SELECT * FROM av_asset_locations
        WHERE assetKey = :assetKey
        ORDER BY isPresent DESC, partNumber ASC, fileName ASC
        """
    )
    suspend fun getLocationsByAssetKey(assetKey: String): List<AvAssetLocation>

    @Query(
        """
        SELECT * FROM av_asset_locations
        WHERE assetKey = :assetKey AND isPresent = 1
        ORDER BY partNumber ASC, fileName ASC
        """
    )
    suspend fun getPresentLocationsByAssetKey(assetKey: String): List<AvAssetLocation>

    @Query(
        """
        UPDATE av_asset_locations
        SET isPresent = 0
        WHERE sourceScope = :sourceScope AND lastSeenAt < :scanStartedAt
        """
    )
    suspend fun markStaleLocationsMissing(sourceScope: String, scanStartedAt: Long)

    @Query(
        """
        UPDATE av_asset_locations
        SET contentFingerprint = :contentFingerprint
        WHERE filePath = :filePath
        """
    )
    suspend fun updateContentFingerprint(filePath: String, contentFingerprint: String?)

    @Query(
        """
        UPDATE av_library_assets
        SET metadataCacheKey = NULL,
            metadataSource = NULL,
            cachedTitle = NULL,
            cachedPosterUrl = NULL,
            cachedStudio = NULL,
            cachedReleaseDateEpochDay = NULL,
            hasMetadata = 0,
            metadataResolvedAt = NULL
        """
    )
    suspend fun clearMetadataLinks()
}
