package blackark.app.vr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import blackark.app.vr.R
import blackark.app.vr.data.database.entity.AvAssetLocation
import blackark.app.vr.data.model.AvCastFilterOption
import blackark.app.vr.data.model.AvLibraryWork
import blackark.app.vr.ui.components.EmptyState
import blackark.app.vr.ui.theme.CardBackground
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filters.activeFamily == AvFilterFamily.Studio,
            onClick = { onSetFilterFamily(AvFilterFamily.Studio) },
            label = { Text("Studio") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NetflixRed.copy(alpha = 0.18f),
                selectedLabelColor = TextPrimary,
            ),
        )
        FilterChip(
            selected = filters.activeFamily == AvFilterFamily.Casts,
            onClick = { onSetFilterFamily(AvFilterFamily.Casts) },
            label = { Text("Casts") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NetflixRed.copy(alpha = 0.18f),
                selectedLabelColor = TextPrimary,
            ),
        )
        FilterChip(
            selected = filters.activeFamily == AvFilterFamily.ReleaseDate,
            onClick = { onSetFilterFamily(AvFilterFamily.ReleaseDate) },
            label = { Text("Release Date") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = NetflixRed.copy(alpha = 0.18f),
                selectedLabelColor = TextPrimary,
            ),
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onClearFilters) {
            Text("Clear")
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    when (filters.activeFamily) {
        AvFilterFamily.None -> {
            Text(
                text = "Choose one filter family at a time. Studio, casts, and release date stay independent.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }

        AvFilterFamily.Studio -> {
            StudioFilterSelector(
                studios = avLibrary.studioOptions.map { it.studio },
                selectedStudio = filters.selectedStudio,
                onStudioSelected = onStudioSelected,
            )
        }

        AvFilterFamily.Casts -> {
            CastFilterGrid(
                castOptions = avLibrary.castOptions,
                selectedCastIds = filters.selectedCastIds,
                onCastToggled = onCastToggled,
            )
        }

        AvFilterFamily.ReleaseDate -> {
            ReleaseDateCalendar(
                month = filters.visibleMonth,
                selectedDate = filters.selectedReleaseDate,
                counts = avLibrary.releaseDateCounts.associate { it.date to it.itemCount },
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onDateSelected = onReleaseDateSelected,
            )
        }
    }
}

@Composable
private fun StudioFilterSelector(
    studios: List<String>,
    selectedStudio: String?,
    onStudioSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedStudio ?: "Choose studio")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
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
private fun CastFilterGrid(
    castOptions: List<AvCastFilterOption>,
    selectedCastIds: Set<String>,
    onCastToggled: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 92.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(castOptions, key = { it.performerId }) { cast ->
            val isSelected = cast.performerId in selectedCastIds
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onCastToggled(cast.performerId) },
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = NetflixRed,
                )
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(cells, key = { it?.toEpochDay() ?: Long.MIN_VALUE }) { date ->
                if (date == null) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                } else {
                    val isSelected = selectedDate == date
                    val count = counts[date] ?: 0
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDateSelected(if (isSelected) null else date) },
                        color = if (isSelected) NetflixRed.copy(alpha = 0.16f) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) NetflixRed else DividerGray.copy(alpha = 0.6f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NetflixRed,
                                )
                            }
                        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = work.displayTitle,
                color = TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PosterThumbnail(
                    posterUrl = work.displayPosterUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )

                Text(
                    text = work.asset.normalizedCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 92.dp),
                        modifier = Modifier.heightIn(max = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(work.casts, key = { it.performerId }) { cast ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CastAvatar(
                                    imageUrl = cast.profileImageUrl ?: cast.remoteProfileImageUrl,
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
                    work.parts.forEach { part ->
                        AvPartRow(
                            part = part,
                            onPlay = { onPlayPart(part.filePath, part.fileName) },
                        )
                    }
                }
            }
        },
    )
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
