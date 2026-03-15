package blackark.app.vr.data.repository

import blackark.app.vr.data.database.dao.AvLibraryDao
import blackark.app.vr.data.database.dao.VirtualGroupMetadataDao
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.data.model.AvCastFilterOption
import blackark.app.vr.data.model.AvLibrarySnapshot
import blackark.app.vr.data.model.AvLibraryWork
import blackark.app.vr.data.model.AvReleaseDateCount
import blackark.app.vr.data.model.AvStudioFilterOption
import blackark.app.vr.data.model.AV_CAST_FILTER_NONE_ID
import blackark.app.vr.utils.JvrLibraryMetadataProvider
import java.time.LocalDate

class AvLibraryRepository(
    private val avLibraryDao: AvLibraryDao,
    private val virtualGroupMetadataDao: VirtualGroupMetadataDao,
) {
    suspend fun upsertAsset(asset: AvLibraryAsset) {
        avLibraryDao.upsertAsset(asset)
    }

    suspend fun upsertLocation(location: AvAssetLocation) {
        avLibraryDao.upsertLocation(location)
    }

    suspend fun getAssetByKey(assetKey: String): AvLibraryAsset? {
        return avLibraryDao.getAssetByKey(assetKey)
    }

    suspend fun getAssetBySourceAndCode(sourceScope: String, normalizedCode: String): AvLibraryAsset? {
        return avLibraryDao.getAssetBySourceAndCode(sourceScope, normalizedCode)
    }

    suspend fun findAssetForPathOrCode(
        filePath: String,
        sourceScope: String,
        normalizedCode: String?,
    ): AvLibraryAsset? {
        val byPath = avLibraryDao.getLocationByPath(filePath)?.let { location ->
            avLibraryDao.getAssetByKey(location.assetKey)
        }
        if (byPath != null) return byPath
        if (normalizedCode == null) return null
        return avLibraryDao.getAssetBySourceAndCode(sourceScope, normalizedCode)
    }

    suspend fun getLocationByPath(filePath: String): AvAssetLocation? {
        return avLibraryDao.getLocationByPath(filePath)
    }

    suspend fun getMissingFingerprintCandidates(
        sourceScope: String,
        size: Long,
    ): List<AvAssetLocation> {
        return avLibraryDao.getMissingFingerprintCandidates(sourceScope, size)
    }

    suspend fun getMissingFallbackLocation(
        assetKey: String,
        partNumber: Int?,
        size: Long,
    ): AvAssetLocation? {
        return avLibraryDao.getMissingFallbackLocation(assetKey, partNumber, size)
    }

    suspend fun getLocationsByAssetKey(assetKey: String): List<AvAssetLocation> {
        return avLibraryDao.getLocationsByAssetKey(assetKey)
    }

    suspend fun getPresentLocationsByAssetKey(assetKey: String): List<AvAssetLocation> {
        return avLibraryDao.getPresentLocationsByAssetKey(assetKey)
    }

    suspend fun getPresentAssetsMissingPerformerRefs(sourceScope: String): List<AvLibraryAsset> {
        return avLibraryDao.getPresentAssetsMissingPerformerRefs(sourceScope)
    }

    suspend fun markStaleLocationsMissing(sourceScope: String, scanStartedAt: Long) {
        avLibraryDao.markStaleLocationsMissing(sourceScope, scanStartedAt)
    }

    suspend fun updateContentFingerprint(filePath: String, contentFingerprint: String?) {
        avLibraryDao.updateContentFingerprint(filePath, contentFingerprint)
    }

    suspend fun clearMetadataLinks() {
        avLibraryDao.clearMetadataLinks()
    }

    suspend fun getLinkedPathsForAsset(assetKey: String): List<String> {
        return avLibraryDao.getLocationsByAssetKey(assetKey).map { it.filePath }
    }

    suspend fun loadWorkByAssetKey(assetKey: String): AvLibraryWork? {
        val asset = avLibraryDao.getAssetByKey(assetKey) ?: return null
        val parts = avLibraryDao.getPresentLocationsByAssetKey(assetKey)
        val metadata = asset.metadataCacheKey
            ?.let { cacheKey -> virtualGroupMetadataDao.getByCacheKey(cacheKey) }
            ?.takeUnless { it.metadata.isMiss }
            ?.let { record ->
                JvrLibraryMetadataProvider.toMovieMetadata(
                    record = record,
                    fallbackCode = asset.normalizedCode,
                )
            }

        return AvLibraryWork(
            asset = asset,
            metadata = metadata,
            parts = parts,
        )
    }

    suspend fun loadSnapshot(sourceScope: String): AvLibrarySnapshot {
        val assets = avLibraryDao.getPresentAssetsBySource(sourceScope)
        if (assets.isEmpty()) {
            return AvLibrarySnapshot()
        }

        val locationsByAsset = avLibraryDao.getPresentLocationsBySource(sourceScope)
            .groupBy { it.assetKey }

        val metadataByCacheKey = virtualGroupMetadataDao
            .getByCacheKeys(assets.mapNotNull { it.metadataCacheKey }.distinct())
            .associateBy { it.metadata.cacheKey }

        val works = assets.mapNotNull { asset ->
            val metadata = asset.metadataCacheKey
                ?.let(metadataByCacheKey::get)
                ?.takeUnless { it.metadata.isMiss }
                ?.let { record ->
                    JvrLibraryMetadataProvider.toMovieMetadata(
                        record = record,
                        fallbackCode = asset.normalizedCode,
                    )
                }

            metadata ?: return@mapNotNull null

            AvLibraryWork(
                asset = asset,
                metadata = metadata,
                parts = locationsByAsset[asset.assetKey].orEmpty(),
            )
        }

        return AvLibrarySnapshot(
            works = works,
            studioOptions = buildStudioOptions(works),
            castOptions = buildCastOptions(works),
            releaseDateCounts = buildReleaseDateCounts(works),
        )
    }

    private fun buildStudioOptions(works: List<AvLibraryWork>): List<AvStudioFilterOption> {
        return works
            .mapNotNull { it.studio?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (studio, count) ->
                AvStudioFilterOption(
                    studio = studio,
                    itemCount = count,
                )
            }
    }

    private fun buildCastOptions(works: List<AvLibraryWork>): List<AvCastFilterOption> {
        val optionsById = linkedMapOf<String, AvCastFilterOption>()
        val counts = linkedMapOf<String, Int>()
        var worksWithoutCasts = 0

        works.forEach { work ->
            val uniqueCasts = work.casts
                .distinctBy { it.performerId }
                .filter { it.performerId.isNotBlank() }

            if (uniqueCasts.isEmpty()) {
                worksWithoutCasts += 1
            }

            uniqueCasts.forEach { cast ->
                counts[cast.performerId] = (counts[cast.performerId] ?: 0) + 1
                optionsById.putIfAbsent(
                    cast.performerId,
                    AvCastFilterOption(
                        performerId = cast.performerId,
                        englishName = cast.englishName,
                        japaneseName = cast.japaneseName,
                        profileImageUrl = cast.profileImageUrl ?: cast.remoteProfileImageUrl,
                        itemCount = 0,
                    )
                )
            }
        }

        val castOptions = optionsById.values
            .map { option ->
                option.copy(itemCount = counts[option.performerId] ?: 0)
            }
            .sortedWith(
                compareByDescending<AvCastFilterOption> { it.itemCount }
                    .thenBy { it.japaneseName?.lowercase().orEmpty() }
                    .thenBy { it.englishName.lowercase() }
            )

        return buildList {
            if (worksWithoutCasts > 0) {
                add(
                    AvCastFilterOption(
                        performerId = AV_CAST_FILTER_NONE_ID,
                        englishName = "None",
                        japaneseName = null,
                        profileImageUrl = null,
                        itemCount = worksWithoutCasts,
                    )
                )
            }
            addAll(castOptions)
        }
    }

    private fun buildReleaseDateCounts(works: List<AvLibraryWork>): List<AvReleaseDateCount> {
        return works
            .mapNotNull { it.releaseDate }
            .groupingBy(LocalDate::toEpochDay)
            .eachCount()
            .entries
            .sortedByDescending { it.key }
            .map { (epochDay, count) ->
                AvReleaseDateCount(
                    date = LocalDate.ofEpochDay(epochDay),
                    itemCount = count,
                )
            }
    }
}
