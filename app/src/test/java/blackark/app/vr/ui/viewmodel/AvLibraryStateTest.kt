package blackark.app.vr.ui.viewmodel

import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.database.entity.AvLibraryAsset
import blackark.app.vr.data.model.AvLibrarySnapshot
import blackark.app.vr.data.model.AvLibraryWork
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrMovieMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AvLibraryStateTest {

    @Test
    fun `studio filter keeps only matching works`() {
        val snapshot = AvLibrarySnapshot(works = listOf(
            createWork("asset-1", "A", "Studio A"),
            createWork("asset-2", "B", "Studio B"),
        ))

        val filtered = snapshot.applyFilters(
            AvFilterState(
                activeFamily = AvFilterFamily.Studio,
                selectedStudio = "Studio B",
            )
        )

        assertEquals(listOf("asset-2"), filtered.map { it.assetKey })
    }

    @Test
    fun `casts filter requires all selected performers`() {
        val snapshot = AvLibrarySnapshot(works = listOf(
            createWork(
                assetKey = "asset-1",
                code = "A",
                studio = "Studio A",
                casts = listOf(
                    JvrCastMetadata("p1", "Performer 1"),
                    JvrCastMetadata("p2", "Performer 2"),
                ),
            ),
            createWork(
                assetKey = "asset-2",
                code = "B",
                studio = "Studio A",
                casts = listOf(JvrCastMetadata("p1", "Performer 1")),
            ),
        ))

        val filtered = snapshot.applyFilters(
            AvFilterState(
                activeFamily = AvFilterFamily.Casts,
                selectedCastIds = setOf("p1", "p2"),
            )
        )

        assertEquals(listOf("asset-1"), filtered.map { it.assetKey })
    }

    @Test
    fun `release date filter keeps matching date`() {
        val releaseDate = LocalDate.of(2024, 6, 1)
        val snapshot = AvLibrarySnapshot(works = listOf(
            createWork("asset-1", "A", "Studio A", releaseDate = releaseDate),
            createWork("asset-2", "B", "Studio A", releaseDate = LocalDate.of(2024, 6, 2)),
        ))

        val filtered = snapshot.applyFilters(
            AvFilterState(
                activeFamily = AvFilterFamily.ReleaseDate,
                selectedReleaseDate = releaseDate,
            )
        )

        assertEquals(listOf("asset-1"), filtered.map { it.assetKey })
    }

    private fun createWork(
        assetKey: String,
        code: String,
        studio: String,
        releaseDate: LocalDate? = null,
        casts: List<JvrCastMetadata> = emptyList(),
    ): AvLibraryWork {
        return AvLibraryWork(
            asset = AvLibraryAsset(
                assetKey = assetKey,
                sourceScope = "scope",
                normalizedCode = code,
            ),
            metadata = JvrMovieMetadata(
                code = code,
                title = code,
                posterUrl = null,
                releaseDate = releaseDate,
                studio = studio,
                casts = casts,
            ),
            parts = listOf(
                AvAssetLocation(
                    filePath = "/$code.mp4",
                    assetKey = assetKey,
                    sourceScope = "scope",
                    fileName = "$code.mp4",
                    partNumber = null,
                    size = 1,
                    lastModified = 1,
                )
            ),
        )
    }
}
