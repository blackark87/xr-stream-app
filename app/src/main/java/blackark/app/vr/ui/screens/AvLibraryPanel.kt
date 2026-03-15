package blackark.app.vr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrMovieMetadata
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
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
    onMergeCast: (String, String) -> Unit,
    onAddCastAlias: (String, String?, String?) -> Unit,
    onSaveWorkMetadata: (String, JvrMovieMetadata) -> Unit,
    onPlayPart: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedWork = remember(avLibrary.selectedAssetKey, avLibrary.snapshot.works) {
        avLibrary.selectedAssetKey?.let { key ->
            avLibrary.snapshot.works.firstOrNull { it.assetKey == key }
        }
    }
    var mergeTargetCastId by remember { mutableStateOf<String?>(null) }
    var aliasDialogCast by remember { mutableStateOf<AvCastFilterOption?>(null) }
    var editMetadataWork by remember { mutableStateOf<AvLibraryWork?>(null) }
    val mergeTargetCast = remember(mergeTargetCastId, avLibrary.castOptions) {
        mergeTargetCastId?.let { performerId ->
            avLibrary.castOptions.firstOrNull { it.performerId == performerId }
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
                        mergeTargetCast = mergeTargetCast,
                        onSetMergeTarget = { cast ->
                            mergeTargetCastId = cast.performerId
                        },
                        onMergeWithTarget = { sourceCast, targetCast ->
                            onMergeCast(sourceCast.performerId, targetCast.performerId)
                            mergeTargetCastId = null
                        },
                        onClearMergeTarget = { mergeTargetCastId = null },
                        onAddCastAliasRequested = { cast -> aliasDialogCast = cast },
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
                                        onEditMetadata = { editMetadataWork = work },
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

    aliasDialogCast?.let { cast ->
        AddPerformerAliasDialog(
            cast = cast,
            onDismiss = { aliasDialogCast = null },
            onSave = { englishName, japaneseName ->
                onAddCastAlias(cast.performerId, englishName, japaneseName)
                aliasDialogCast = null
            },
        )
    }

    editMetadataWork?.let { work ->
        EditAvMetadataDialog(
            work = work,
            onDismiss = { editMetadataWork = null },
            onSave = { metadata ->
                onSaveWorkMetadata(work.assetKey, metadata)
                editMetadataWork = null
            },
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
    mergeTargetCast: AvCastFilterOption?,
    onSetMergeTarget: (AvCastFilterOption) -> Unit,
    onMergeWithTarget: (AvCastFilterOption, AvCastFilterOption) -> Unit,
    onClearMergeTarget: () -> Unit,
    onAddCastAliasRequested: (AvCastFilterOption) -> Unit,
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
            mergeTargetCast?.let { target ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NetflixRed.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Merge target: ${buildCastLabel(target.japaneseName, target.englishName)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = NetflixRed,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearMergeTarget) {
                            Text("Cancel")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (mergeTargetCast == null) {
                Text(
                    text = "Long press a cast card to set or merge aliases.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            CastFilterGrid(
                castOptions = avLibrary.castOptions,
                selectedCastIds = filters.selectedCastIds,
                onCastToggled = onCastToggled,
                mergeTargetCast = mergeTargetCast,
                onSetMergeTarget = onSetMergeTarget,
                onMergeWithTarget = onMergeWithTarget,
                onClearMergeTarget = onClearMergeTarget,
                onAddCastAliasRequested = onAddCastAliasRequested,
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
    mergeTargetCast: AvCastFilterOption?,
    onSetMergeTarget: (AvCastFilterOption) -> Unit,
    onMergeWithTarget: (AvCastFilterOption, AvCastFilterOption) -> Unit,
    onClearMergeTarget: () -> Unit,
    onAddCastAliasRequested: (AvCastFilterOption) -> Unit,
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
                        mergeTargetCast = mergeTargetCast,
                        onSetMergeTarget = { onSetMergeTarget(cast) },
                        onMergeWithTarget = { target -> onMergeWithTarget(cast, target) },
                        onClearMergeTarget = onClearMergeTarget,
                        onAddCastAliasRequested = { onAddCastAliasRequested(cast) },
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
    onEditMetadata: () -> Unit,
) {
    var menuExpanded by remember(work.assetKey) { mutableStateOf(false) }
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
            val castSummary = buildWorkCastSummary(work.casts)
            PosterThumbnail(
                posterUrl = work.displayPosterUrl,
                showVrBadge = shouldShowVrBadge(work),
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
                castSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (work.parts.size > 1) {
                    Text(
                        text = "${work.parts.size} parts",
                        style = MaterialTheme.typography.labelMedium,
                        color = NetflixRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (isSelected) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More",
                                tint = TextSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = CardBackgroundHover,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit metadata") },
                                onClick = {
                                    menuExpanded = false
                                    onEditMetadata()
                                },
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = NetflixRed,
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = TextSecondary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = CardBackgroundHover,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit metadata") },
                            onClick = {
                                menuExpanded = false
                                onEditMetadata()
                            },
                        )
                    }
                }
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
            modifier = Modifier
                .padding(12.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = NetflixRed.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.55f),
                ),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, NetflixRed.copy(alpha = 0.38f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
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
                            onPlay = { onPlayPart(part.filePath, part.fileName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPerformerAliasDialog(
    cast: AvCastFilterOption,
    onDismiss: () -> Unit,
    onSave: (String?, String?) -> Unit,
) {
    var englishName by remember(cast.performerId) { mutableStateOf(cast.englishName) }
    var japaneseName by remember(cast.performerId) { mutableStateOf(cast.japaneseName.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Add Alias",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = buildCastLabel(cast.japaneseName, cast.englishName),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                OutlinedTextField(
                    value = japaneseName,
                    onValueChange = { japaneseName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Japanese name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = englishName,
                    onValueChange = { englishName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("English name") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            onSave(
                                englishName.trim().takeIf { it.isNotBlank() },
                                japaneseName.trim().takeIf { it.isNotBlank() },
                            )
                        },
                        enabled = englishName.isNotBlank() || japaneseName.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditAvMetadataDialog(
    work: AvLibraryWork,
    onDismiss: () -> Unit,
    onSave: (JvrMovieMetadata) -> Unit,
) {
    var title by remember(work.assetKey) { mutableStateOf(work.displayTitle) }
    var studio by remember(work.assetKey) { mutableStateOf(work.studio.orEmpty()) }
    var releaseDateText by remember(work.assetKey) {
        mutableStateOf(work.releaseDate?.toString().orEmpty())
    }
    var editableCasts by remember(work.assetKey) { mutableStateOf(work.casts) }
    var newCastJapanese by remember(work.assetKey) { mutableStateOf("") }
    var newCastEnglish by remember(work.assetKey) { mutableStateOf("") }
    var releaseDateError by remember(work.assetKey) { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Edit Metadata",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Text(
                    text = work.asset.normalizedCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                )
                OutlinedTextField(
                    value = studio,
                    onValueChange = { studio = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Studio") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = releaseDateText,
                    onValueChange = {
                        releaseDateText = it
                        releaseDateError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Release date (YYYY-MM-DD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = releaseDateError != null,
                )
                releaseDateError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = NetflixRed,
                    )
                }

                Text(
                    text = "Casts",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                if (editableCasts.isEmpty()) {
                    Text(
                        text = "No casts added yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        editableCasts.forEach { cast ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.7f)),
                                color = Color.Transparent,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = buildCastLabel(cast.japaneseName, cast.englishName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = {
                                            editableCasts = editableCasts.filterNot {
                                                it.performerId == cast.performerId
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove cast",
                                            tint = TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "Add Cast",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                )
                OutlinedTextField(
                    value = newCastJapanese,
                    onValueChange = { newCastJapanese = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Japanese name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newCastEnglish,
                    onValueChange = { newCastEnglish = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("English name") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val trimmedJapanese = newCastJapanese.trim().takeIf { it.isNotBlank() }
                            val trimmedEnglish = newCastEnglish.trim().takeIf { it.isNotBlank() }
                            if (trimmedJapanese != null || trimmedEnglish != null) {
                                val fallback = trimmedJapanese ?: trimmedEnglish ?: "manual-cast"
                                editableCasts = (editableCasts + JvrCastMetadata(
                                    performerId = "manual:${fallback.lowercase()}",
                                    englishName = trimmedEnglish ?: trimmedJapanese.orEmpty(),
                                    japaneseName = trimmedJapanese,
                                )).distinctBy { buildCastLabel(it.japaneseName, it.englishName) }
                                newCastJapanese = ""
                                newCastEnglish = ""
                            }
                        },
                    ) {
                        Text("Add")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsedReleaseDate = when {
                                releaseDateText.isBlank() -> null
                                else -> runCatching { LocalDate.parse(releaseDateText.trim()) }
                                    .getOrElse {
                                        releaseDateError = "Use YYYY-MM-DD"
                                        return@Button
                                    }
                            }

                            onSave(
                                JvrMovieMetadata(
                                    code = work.metadata?.code ?: work.asset.normalizedCode,
                                    title = title.trim().ifBlank { work.asset.normalizedCode },
                                    posterUrl = work.metadata?.posterUrl ?: work.displayPosterUrl,
                                    releaseDate = parsedReleaseDate,
                                    studio = studio.trim().takeIf { it.isNotBlank() },
                                    genres = work.metadata?.genres.orEmpty(),
                                    casts = editableCasts,
                                )
                            )
                        },
                    ) {
                        Text("Save")
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
    mergeTargetCast: AvCastFilterOption?,
    onSetMergeTarget: () -> Unit,
    onMergeWithTarget: (AvCastFilterOption) -> Unit,
    onClearMergeTarget: () -> Unit,
    onAddCastAliasRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember(cast.performerId) { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                CastAvatar(
                    imageUrl = cast.profileImageUrl,
                    placeholderText = if (
                        cast.englishName.isBlank() &&
                        cast.japaneseName.isNullOrBlank()
                    ) "?" else "\uD83D\uDC64",
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

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = CardBackgroundHover,
            shape = RoundedCornerShape(14.dp),
        ) {
            if (mergeTargetCast?.performerId == cast.performerId) {
                DropdownMenuItem(
                    text = { Text("Clear merge target") },
                    onClick = {
                        menuExpanded = false
                        onClearMergeTarget()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Set merge target") },
                    onClick = {
                        menuExpanded = false
                        onSetMergeTarget()
                    },
                )

                mergeTargetCast?.let { target ->
                    DropdownMenuItem(
                        text = {
                            Text("Merge into ${buildCastLabel(target.japaneseName, target.englishName)}")
                        },
                        onClick = {
                            menuExpanded = false
                            onMergeWithTarget(target)
                        },
                    )
                }
            }

            DropdownMenuItem(
                text = { Text("Add alias") },
                onClick = {
                    menuExpanded = false
                    onAddCastAliasRequested()
                },
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
            ) "?" else "\uD83D\uDC64",
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
    onPlay: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.7f)),
        color = Color.Transparent,
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
                    text = part.partNumber?.let { "Part $it" } ?: "Single file",
                    style = MaterialTheme.typography.labelLarge,
                    color = NetflixRed,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = part.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
    showVrBadge: Boolean = false,
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

        if (showVrBadge) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.55f)),
            ) {
                Text(
                    text = "VR",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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

private fun buildWorkCastSummary(casts: List<blackark.app.vr.utils.JvrCastMetadata>): String? {
    if (casts.isEmpty()) return null

    val visibleLabels = casts
        .map { buildCastLabel(it.japaneseName, it.englishName) }
        .filter { it.isNotBlank() && it != "?" }
        .distinct()
        .take(3)

    if (visibleLabels.isEmpty()) return null

    val remainingCount = casts
        .map { it.performerId }
        .distinct()
        .size - visibleLabels.size

    return buildString {
        append(visibleLabels.joinToString("  •  "))
        if (remainingCount > 0) {
            append("  •  +")
            append(remainingCount)
        }
    }
}

private fun shouldShowVrBadge(work: AvLibraryWork): Boolean {
    val representativePath = work.representativePath.orEmpty().replace('\\', '/')
    val representativeFileName = work.representativeFileName.orEmpty()
    val stereoPattern = Regex(
        "(?i)(?:\\bVR\\b|\\b8KVR\\b|\\bVR8K\\b|(?:^|[ _.\\-])SBS(?:$|[ _.\\-])|(?:^|[ _.\\-])TB(?:$|[ _.\\-]))"
    )

    return work.asset.metadataSource.equals("jvr", ignoreCase = true) ||
        Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE).containsMatchIn(representativePath) ||
        stereoPattern.containsMatchIn(representativeFileName)
}
