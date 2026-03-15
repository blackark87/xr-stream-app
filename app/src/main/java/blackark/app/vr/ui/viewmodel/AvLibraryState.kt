package blackark.app.vr.ui.viewmodel

import blackark.app.vr.data.model.AvCastFilterOption
import blackark.app.vr.data.model.AvLibrarySnapshot
import blackark.app.vr.data.model.AvLibraryWork
import blackark.app.vr.data.model.AvReleaseDateCount
import blackark.app.vr.data.model.AvStudioFilterOption
import java.time.LocalDate
import java.time.YearMonth

enum class AvFilterFamily {
    None,
    Studio,
    Casts,
    ReleaseDate,
}

data class AvFilterState(
    val activeFamily: AvFilterFamily = AvFilterFamily.None,
    val selectedStudio: String? = null,
    val selectedCastIds: Set<String> = emptySet(),
    val selectedReleaseDate: LocalDate? = null,
    val visibleMonth: YearMonth = YearMonth.now(),
)

data class AvScanState(
    val isRunning: Boolean = false,
    val currentPath: String? = null,
    val scannedFileCount: Int = 0,
    val discoveredWorkCount: Int = 0,
    val lastCompletedAt: Long? = null,
    val errorMessage: String? = null,
)

data class AvLibraryState(
    val snapshot: AvLibrarySnapshot = AvLibrarySnapshot(),
    val filteredWorks: List<AvLibraryWork> = emptyList(),
    val filters: AvFilterState = AvFilterState(),
    val scan: AvScanState = AvScanState(),
    val selectedAssetKey: String? = null,
) {
    val studioOptions: List<AvStudioFilterOption>
        get() = snapshot.studioOptions

    val castOptions: List<AvCastFilterOption>
        get() = snapshot.castOptions

    val releaseDateCounts: List<AvReleaseDateCount>
        get() = snapshot.releaseDateCounts

    val works: List<AvLibraryWork>
        get() = snapshot.works
}

fun AvLibrarySnapshot.applyFilters(filters: AvFilterState): List<AvLibraryWork> {
    if (works.isEmpty()) return emptyList()

    val filtered = when (filters.activeFamily) {
        AvFilterFamily.None -> works

        AvFilterFamily.Studio -> {
            val selectedStudio = filters.selectedStudio?.trim()?.takeIf { it.isNotBlank() }
            if (selectedStudio == null) {
                works
            } else {
                works.filter { work -> work.studio == selectedStudio }
            }
        }

        AvFilterFamily.Casts -> {
            if (filters.selectedCastIds.isEmpty()) {
                works
            } else {
                works.filter { work ->
                    val castIds = work.casts.map { it.performerId }.toSet()
                    filters.selectedCastIds.any(castIds::contains)
                }
            }
        }

        AvFilterFamily.ReleaseDate -> {
            val selectedDate = filters.selectedReleaseDate
            if (selectedDate == null) {
                works
            } else {
                works.filter { work -> work.releaseDate == selectedDate }
            }
        }
    }

    return filtered.sortedWith(
        compareByDescending<AvLibraryWork> { it.releaseDate?.toEpochDay() ?: Long.MIN_VALUE }
            .thenBy { it.displayTitle.lowercase() }
            .thenBy { it.asset.normalizedCode.lowercase() }
    )
}
