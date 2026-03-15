package blackark.app.vr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import blackark.app.vr.R
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.model.AvCastFilterOption
import blackark.app.vr.data.model.AvLibraryWork
import blackark.app.vr.ui.components.EmptyState
import blackark.app.vr.ui.theme.CardBackground
import blackark.app.vr.ui.theme.CardBackgroundHover
import blackark.app.vr.ui.theme.DividerGray
import blackark.app.vr.ui.theme.NetflixRed
import blackark.app.vr.ui.theme.TextPrimary
import blackark.app.vr.ui.theme.TextSecondary
import blackark.app.vr.ui.theme.TextTertiary
import blackark.app.vr.ui.viewmodel.AvFilterFamily
import blackark.app.vr.ui.viewmodel.AvLibraryState
import coil3.compose.AsyncImage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AvLibraryPanel(
    avLibrary: AvLibraryState,
    isConnected: Boolean,
    backgroundIndexingEnabled: Boolean,
    onSetFilterFamily: (AvFilterFamily) -> Unit,
    onStudioSelected: (String?) -> Unit,
    onCastToggled: (String) -> Unit,
    onReleaseDateSelected: (LocalDate?) -> Unit,
    onClearFilters: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onWorkSelected: (String?) -> Unit,
    onPlayPart: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedWork = remember(avLibrary.selectedAssetKey, avLibrary.snapshot.works) {
        avLibrary.selectedAssetKey?.let { key ->
            avLibrary.snapshot.works.firstOrNull { it.assetKey == key }
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = NetflixRed,
                    )
                    Column {
                        Text(
                            text = "AV",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                        )
                        val statusText = when {
                            !backgroundIndexingEnabled -> "Background indexing is off"
                            avLibrary.scan.isRunning -> "Scanning ${avLibrary.scan.scannedFileCount} files"
                            avLibrary.scan.lastCompletedAt != null -> "Indexed ${avLibrary.snapshot.works.size} works"
                            else -> "Metadata-driven library"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }

                if (avLibrary.scan.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NetflixRed,
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Filled.MovieCreation,
                        message = "Connect to open AV metadata filters",
                    )
                }

                else -> {
                    AvFilterSection(
                        avLibrary = avLibrary,
                        onSetFilterFamily = onSetFilterFamily,
                        onStudioSelected = onStudioSelected,
                        onCastToggled = onCastToggled,
                        onReleaseDateSelected = onReleaseDateSelected,
                        onClearFilters = onClearFilters,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DividerGray)
                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        avLibrary.snapshot.works.isEmpty() && !backgroundIndexingEnabled -> {
                            EmptyState(
                                icon = Icons.Filled.MovieCreation,
                                message = "Enable AV background indexing in Settings to build the filter library.",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        avLibrary.filteredWorks.isEmpty() && avLibrary.scan.isRunning -> {
                            EmptyState(
                                icon = Icons.Filled.MovieCreation,
                                message = "Scanning the connected source for AV works...",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        avLibrary.filteredWorks.isEmpty() -> {
                            EmptyState(
                                icon = Icons.Filled.MovieCreation,
                                message = "No works match the current filter",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 6.dp),
                            ) {
                                items(avLibrary.filteredWorks, key = { it.assetKey }) { work ->
                                    AvWorkCard(
                                        work = work,
                                        isSelected = avLibrary.selectedAssetKey == work.assetKey,
                                        onClick = { onWorkSelected(work.assetKey) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedWork != null) {
        AvWorkDetailDialog(
            work = selectedWork,
            onDismiss = { onWorkSelected(null) },
            onPlayPart = onPlayPart,
        )
    }
}

@Composable
private fun AvFilterSection(
    avLibrary: AvLibraryState,
    onSetFilterFamily: (AvFilterFamily) -> Unit,
    onStudioSelected: (String?) -> Unit,
    onCastToggled: (String) -> Unit,
    onReleaseDateSelected: (LocalDate?) -> Unit,
    onClearFilters: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val filters = avLibrary.filters

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StudioFilterTrigger(
            studios = avLibrary.studioOptions.map { it.studio },
            selectedStudio = filters.selectedStudio,
            onStudioSelected = onStudioSelected,
            modifier = Modifier.widthIn(min = 136.dp, max = 200.dp),
        )
        FilterTriggerButton(
            label = if (filters.selectedCastIds.isEmpty()) {
                "Casts"
            } else {
                "Casts (${filters.selectedCastIds.size})"
            },
            selected = filters.activeFamily == AvFilterFamily.Casts || filters.selectedCastIds.isNotEmpty(),
            onClick = {
                if (filters.activeFamily == AvFilterFamily.Casts && filters.selectedCastIds.isEmpty()) {
                    onSetFilterFamily(AvFilterFamily.None)
                } else {
                    onSetFilterFamily(AvFilterFamily.Casts)
                }
            },
            modifier = Modifier.widthIn(min = 116.dp, max = 160.dp),
        )
        ReleaseDateFilterTrigger(
            month = filters.visibleMonth,
            selectedDate = filters.selectedReleaseDate,
            counts = avLibrary.releaseDateCounts.associate { it.date to it.itemCount },
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            onDateSelected = onReleaseDateSelected,
            modifier = Modifier.widthIn(min = 124.dp, max = 168.dp),
        )
        if (
            filters.selectedStudio != null ||
            filters.selectedCastIds.isNotEmpty() ||
            filters.selectedReleaseDate != null
        ) {
            TextButton(onClick = onClearFilters) {
                Text("Clear")
            }
        }
    }

    when (filters.activeFamily) {
        AvFilterFamily.None -> {
            if (filters.selectedStudio != null || filters.selectedReleaseDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildFilterSummary(
                        studio = filters.selectedStudio,
                        releaseDate = filters.selectedReleaseDate,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }

        AvFilterFamily.Studio -> Unit

        AvFilterFamily.Casts -> {
            Spacer(modifier = Modifier.height(12.dp))
            CastFilterGrid(
                castOptions = avLibrary.castOptions,
                selectedCastIds = filters.selectedCastIds,
                onCastToggled = onCastToggled,
            )
        }

        AvFilterFamily.ReleaseDate -> Unit
    }

}

@Composable
private fun StudioFilterTrigger(
    studios: List<String>,
    selectedStudio: String?,
    onStudioSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilterTriggerButton(
            label = selectedStudio ?: "Studio",
            selected = selectedStudio != null,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CardBackgroundHover,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .background(CardBackgroundHover)
                .widthIn(min = 200.dp, max = 260.dp),
        ) {
            DropdownMenuItem(
                text = { Text("All studios") },
                onClick = {
                    expanded = false
                    onStudioSelected(null)
                },
            )
            studios.forEach { studio ->
                DropdownMenuItem(
                    text = { Text(studio) },
                    onClick = {
                        expanded = false
                        onStudioSelected(studio)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterTriggerButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(
            1.dp,
            if (selected) NetflixRed else DividerGray.copy(alpha = 0.7f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) NetflixRed.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = TextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (selected) NetflixRed else TextSecondary,
            )
        }
    }
}

private fun buildReleaseDateLabel(selectedDate: LocalDate?): String {
    return selectedDate?.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) ?: "Release date"
}

private fun buildFilterSummary(
    studio: String?,
    releaseDate: LocalDate?,
): String {
    val parts = buildList {
        if (!studio.isNullOrBlank()) add("Studio: $studio")
        if (releaseDate != null) add("Release date: $releaseDate")
    }
    return parts.joinToString("  •  ")
}

@Composable
private fun CastFilterGrid(
    castOptions: List<AvCastFilterOption>,
    selectedCastIds: Set<String>,
    onCastToggled: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        castOptions.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { cast ->
                    CastFilterCell(
                        cast = cast,
                        isSelected = cast.performerId in selectedCastIds,
                        onClick = { onCastToggled(cast.performerId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat((4 - row.size).coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReleaseDateFilterTrigger(
    month: YearMonth,
    selectedDate: LocalDate?,
    counts: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        FilterTriggerButton(
            label = buildReleaseDateLabel(selectedDate),
            selected = selectedDate != null,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CardBackgroundHover,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .background(CardBackgroundHover)
                .widthIn(min = 252.dp, max = 280.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReleaseDateCalendar(
                    month = month,
                    selectedDate = selectedDate,
                    counts = counts,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onDateSelected = { date ->
                        onDateSelected(date)
                        expanded = false
                    },
                )

                if (selectedDate != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                onDateSelected(null)
                                expanded = false
                            },
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseDateCalendar(
    month: YearMonth,
    selectedDate: LocalDate?,
    counts: Map<LocalDate, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
) {
    val monthLabel = remember(month) {
        month.format(DateTimeFormatter.ofPattern("yyyy MMMM", Locale.US))
    }
    val daysInMonth = remember(month) { month.lengthOfMonth() }
    val firstDay = remember(month) { month.atDay(1) }
    val leadingEmpty = remember(month) {
        (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    }
    val cells = remember(month) {
        buildList<LocalDate?> {
            repeat(leadingEmpty) { add(null) }
            repeat(daysInMonth) { offset ->
                add(month.atDay(offset + 1))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 252.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
                Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = NetflixRed,
                    )
                    Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
            }
                IconButton(onClick = onNextMonth) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        week.forEach { date ->
                            if (date == null) {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            } else {
                                val isSelected = selectedDate == date
                                val count = counts[date] ?: 0
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onDateSelected(if (isSelected) null else date) },
                                    color = if (isSelected) {
                                        NetflixRed.copy(alpha = 0.16f)
                                    } else {
                                        CardBackground
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) NetflixRed else DividerGray.copy(alpha = 0.7f),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                        )
                                        Text(
                                            text = if (count > 0) count.toString() else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (count > 0) NetflixRed else Color.Transparent,
                                        )
                                    }
                                }
                            }
                        }
                        repeat((7 - week.size).coerceAtLeast(0)) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun AvWorkCard(
    work: AvLibraryWork,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (isSelected) NetflixRed else DividerGray.copy(alpha = 0.65f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterThumbnail(
                posterUrl = work.displayPosterUrl,
                modifier = Modifier.size(width = 108.dp, height = 144.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = work.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = work.asset.normalizedCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                work.studio?.let { studio ->
                    Text(
                        text = studio,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                work.releaseDate?.let { releaseDate ->
                    Text(
                        text = releaseDate.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Text(
                    text = "${work.parts.size} part${if (work.parts.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NetflixRed,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = NetflixRed,
                )
            }
        }
    }
}

@Composable
private fun AvWorkDetailDialog(
    work: AvLibraryWork,
    onDismiss: () -> Unit,
    onPlayPart: (String, String) -> Unit,
) {
    val sortedParts = remember(work.parts) { work.sortedParts }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.7f)),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = work.displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                        )
                        Text(
                            text = work.asset.normalizedCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                PosterThumbnail(
                    posterUrl = work.displayPosterUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                work.studio?.let {
                    Text(
                        text = "Studio: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
                work.releaseDate?.let {
                    Text(
                        text = "Release date: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
                work.representativeFileName?.let {
                    Surface(
                        color = CardBackgroundHover,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.55f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Representative file",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                        }
                    }
                }

                if (work.casts.isNotEmpty()) {
                    Text(
                        text = "Casts",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                    )
                    Column(
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        work.casts.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { cast ->
                                    CastDisplayCell(
                                        imageUrl = cast.profileImageUrl ?: cast.remoteProfileImageUrl,
                                        japaneseName = cast.japaneseName,
                                        englishName = cast.englishName,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat((3 - row.size).coerceAtLeast(0)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Parts",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedParts.forEach { part ->
                        AvPartRow(
                            part = part,
                            isRepresentative = part.filePath == work.representativePath,
                            onPlay = { onPlayPart(part.filePath, part.fileName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CastFilterCell(
    cast: AvCastFilterOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) {
            NetflixRed.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) NetflixRed else DividerGray.copy(alpha = 0.7f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CastAvatar(
                imageUrl = cast.profileImageUrl,
                placeholderText = if (
                    cast.englishName.isBlank() &&
                    cast.japaneseName.isNullOrBlank()
                ) "?" else "\uD83D\uDC69",
            )
            Text(
                text = buildCastLabel(cast.japaneseName, cast.englishName),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = cast.itemCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun CastDisplayCell(
    imageUrl: String?,
    japaneseName: String?,
    englishName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CastAvatar(
            imageUrl = imageUrl,
            placeholderText = if (
                englishName.isBlank() &&
                japaneseName.isNullOrBlank()
            ) "?" else "\uD83D\uDC69",
        )
        Text(
            text = buildCastLabel(japaneseName, englishName),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvPartRow(
    part: AvAssetLocation,
    isRepresentative: Boolean,
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (isRepresentative) NetflixRed.copy(alpha = 0.8f) else DividerGray.copy(alpha = 0.7f),
        ),
        color = if (isRepresentative) NetflixRed.copy(alpha = 0.08f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = part.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = part.partNumber?.let { "Part $it" } ?: "Single file",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                if (isRepresentative) {
                    Text(
                        text = "Representative",
                        style = MaterialTheme.typography.labelSmall,
                        color = NetflixRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = NetflixRed,
                )
            }
        }
    }
}

@Composable
private fun PosterThumbnail(
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DividerGray.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MovieCreation,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun CastAvatar(
    imageUrl: String?,
    placeholderText: String,
) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(DividerGray.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = placeholderText,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
    }
}

private fun buildCastLabel(japaneseName: String?, englishName: String): String {
    val japanese = japaneseName?.takeIf { it.isNotBlank() }
    val english = englishName.takeIf { it.isNotBlank() }
    return when {
        japanese != null && english != null && !english.equals(japanese, ignoreCase = true) -> {
            "$japanese ($english)"
        }

        japanese != null -> japanese
        english != null -> english
        else -> "?"
    }
}
