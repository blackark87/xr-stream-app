package blackark.app.vr.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.MovePolicy
import androidx.xr.compose.subspace.ResizePolicy
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.DpVolumeSize
import blackark.app.vr.BuildConfig
import blackark.app.vr.R
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.ui.components.EmptyState
import blackark.app.vr.ui.components.FancyFileCard
import blackark.app.vr.ui.components.FancyMovieCard
import blackark.app.vr.ui.components.FancyServerCard
import blackark.app.vr.ui.components.LocalStorageCard
import blackark.app.vr.ui.navigation.Screen
import blackark.app.vr.ui.theme.CardBackground
import blackark.app.vr.ui.theme.CardBackgroundHover
import blackark.app.vr.ui.theme.DividerGray
import blackark.app.vr.ui.theme.ErrorRed
import blackark.app.vr.ui.theme.NetflixRed
import blackark.app.vr.ui.theme.StreamingBlack
import blackark.app.vr.ui.theme.SuccessGreen
import blackark.app.vr.ui.theme.TextPrimary
import blackark.app.vr.ui.theme.TextSecondary
import blackark.app.vr.ui.theme.TextTertiary
import blackark.app.vr.ui.viewmodel.FileBrowserViewMode
import blackark.app.vr.ui.viewmodel.MainDashboardViewModel
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrLibraryMetadataProvider
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.ServerCredentialAutofillStore
import blackark.app.vr.utils.ThumbnailImageLoaderProvider
import blackark.app.vr.utils.VideoThumbnailFetcher
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.Disposable
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Main Dashboard Screen - Material 3 dashboard with XR pane layout support.
 */
private enum class DashboardPaneDestination(val label: String) {
    Servers("Servers"),
    Browser("Browse"),
    Library("Library"),
}

private enum class DashboardPaneLayoutMode(val label: String) {
    Balanced("Balanced"),
    Focused("Focused");

    fun toggle(): DashboardPaneLayoutMode = if (this == Balanced) Focused else Balanced
}

private enum class DashboardWidthClass {
    Compact,
    Medium,
    Expanded,
}

private enum class ConnectedDashboardTab {
    Files,
    Library,
    Settings,
}

private data class DashboardPaneVisibility(
    val showServers: Boolean,
    val showBrowser: Boolean,
    val showLibrary: Boolean,
)

private data class DashboardPaneWeights(
    val servers: Float,
    val browser: Float,
    val library: Float,
)

private data class DashboardPreviewItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val previewSpec: GroupHoverPreviewSpec?,
    val metadata: JvrMovieMetadata? = null,
    val metadataLookupRequest: VideoMetadataLookupRequest? = null,
    val onOpen: (() -> Unit)? = null,
)

private fun DashboardPaneDestination.icon(): ImageVector = when (this) {
    DashboardPaneDestination.Servers -> Icons.Filled.Cloud
    DashboardPaneDestination.Browser -> Icons.Filled.FolderOpen
    DashboardPaneDestination.Library -> Icons.Filled.Favorite
}

private fun resolveDashboardWidthClass(width: Dp): DashboardWidthClass = when {
    width >= 1500.dp -> DashboardWidthClass.Expanded
    width >= 1050.dp -> DashboardWidthClass.Medium
    else -> DashboardWidthClass.Compact
}

private fun resolvePaneVisibility(
    widthClass: DashboardWidthClass,
    selectedPane: DashboardPaneDestination,
    paneLayoutMode: DashboardPaneLayoutMode,
): DashboardPaneVisibility = when (widthClass) {
    DashboardWidthClass.Expanded -> DashboardPaneVisibility(
        showServers = true,
        showBrowser = true,
        showLibrary = true,
    )

    DashboardWidthClass.Medium -> {
        if (paneLayoutMode == DashboardPaneLayoutMode.Focused) {
            when (selectedPane) {
                DashboardPaneDestination.Servers -> DashboardPaneVisibility(
                    showServers = true,
                    showBrowser = false,
                    showLibrary = false,
                )

                DashboardPaneDestination.Browser -> DashboardPaneVisibility(
                    showServers = false,
                    showBrowser = true,
                    showLibrary = false,
                )

                DashboardPaneDestination.Library -> DashboardPaneVisibility(
                    showServers = false,
                    showBrowser = false,
                    showLibrary = true,
                )
            }
        } else {
            when (selectedPane) {
                DashboardPaneDestination.Servers -> DashboardPaneVisibility(
                    showServers = true,
                    showBrowser = true,
                    showLibrary = false,
                )

                DashboardPaneDestination.Browser,
                DashboardPaneDestination.Library -> DashboardPaneVisibility(
                    showServers = false,
                    showBrowser = true,
                    showLibrary = true,
                )
            }
        }
    }

    DashboardWidthClass.Compact -> when (selectedPane) {
        DashboardPaneDestination.Servers -> DashboardPaneVisibility(
            showServers = true,
            showBrowser = false,
            showLibrary = false,
        )

        DashboardPaneDestination.Browser -> DashboardPaneVisibility(
            showServers = false,
            showBrowser = true,
            showLibrary = false,
        )

        DashboardPaneDestination.Library -> DashboardPaneVisibility(
            showServers = false,
            showBrowser = false,
            showLibrary = true,
        )
    }
}

private fun resolvePaneWeights(
    widthClass: DashboardWidthClass,
    paneLayoutMode: DashboardPaneLayoutMode,
    selectedPane: DashboardPaneDestination,
): DashboardPaneWeights = when (widthClass) {
    DashboardWidthClass.Expanded -> {
        if (paneLayoutMode == DashboardPaneLayoutMode.Balanced) {
            DashboardPaneWeights(
                servers = 0.22f,
                browser = 0.56f,
                library = 0.22f,
            )
        } else {
            when (selectedPane) {
                DashboardPaneDestination.Servers -> DashboardPaneWeights(
                    servers = 0.32f,
                    browser = 0.46f,
                    library = 0.22f,
                )

                DashboardPaneDestination.Browser -> DashboardPaneWeights(
                    servers = 0.18f,
                    browser = 0.64f,
                    library = 0.18f,
                )

                DashboardPaneDestination.Library -> DashboardPaneWeights(
                    servers = 0.22f,
                    browser = 0.46f,
                    library = 0.32f,
                )
            }
        }
    }

    DashboardWidthClass.Medium -> {
        if (paneLayoutMode == DashboardPaneLayoutMode.Focused) {
            when (selectedPane) {
                DashboardPaneDestination.Servers -> DashboardPaneWeights(
                    servers = 1f,
                    browser = 0f,
                    library = 0f,
                )

                DashboardPaneDestination.Browser -> DashboardPaneWeights(
                    servers = 0f,
                    browser = 1f,
                    library = 0f,
                )

                DashboardPaneDestination.Library -> DashboardPaneWeights(
                    servers = 0f,
                    browser = 0f,
                    library = 1f,
                )
            }
        } else {
            when (selectedPane) {
                DashboardPaneDestination.Servers -> DashboardPaneWeights(
                    servers = 0.36f,
                    browser = 0.64f,
                    library = 0f,
                )

                DashboardPaneDestination.Browser -> DashboardPaneWeights(
                    servers = 0f,
                    browser = 0.62f,
                    library = 0.38f,
                )

                DashboardPaneDestination.Library -> DashboardPaneWeights(
                    servers = 0f,
                    browser = 0.58f,
                    library = 0.42f,
                )
            }
        }
    }

    DashboardWidthClass.Compact -> when (selectedPane) {
        DashboardPaneDestination.Servers -> DashboardPaneWeights(
            servers = 1f,
            browser = 0f,
            library = 0f,
        )

        DashboardPaneDestination.Browser -> DashboardPaneWeights(
            servers = 0f,
            browser = 1f,
            library = 0f,
        )

        DashboardPaneDestination.Library -> DashboardPaneWeights(
            servers = 0f,
            browser = 0f,
            library = 1f,
        )
    }
}

@Composable
private fun DashboardNavigationRail(
    selectedPane: DashboardPaneDestination,
    paneLayoutMode: DashboardPaneLayoutMode,
    widthClass: DashboardWidthClass,
    onPaneSelected: (DashboardPaneDestination) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onAddServerClick: () -> Unit,
    destinations: List<DashboardPaneDestination> = DashboardPaneDestination.values().toList(),
    showLayoutToggle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = CardBackground.copy(alpha = 0.92f),
    ) {
        Surface(
            color = NetflixRed.copy(alpha = 0.18f),
            shape = RoundedCornerShape(14.dp),
        ) {
            IconButton(onClick = onAddServerClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add server",
                    tint = NetflixRed,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        destinations.forEach { destination ->
            NavigationRailItem(
                selected = selectedPane == destination,
                onClick = { onPaneSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = widthClass == DashboardWidthClass.Compact,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = TextPrimary,
                    selectedTextColor = TextPrimary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor = NetflixRed.copy(alpha = 0.26f),
                ),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showLayoutToggle) {
            TextButton(onClick = onToggleLayoutMode) {
                Icon(
                    imageVector = if (paneLayoutMode == DashboardPaneLayoutMode.Balanced) {
                        Icons.Filled.ViewModule
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                    contentDescription = "Toggle pane layout",
                    tint = TextSecondary,
                )
            }

            Text(
                text = paneLayoutMode.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun ConnectedSectionRail(
    selectedTab: ConnectedDashboardTab,
    onTabSelected: (ConnectedDashboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = CardBackground.copy(alpha = 0.92f),
    ) {
        NavigationRailItem(
            selected = selectedTab == ConnectedDashboardTab.Files,
            onClick = { onTabSelected(ConnectedDashboardTab.Files) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "Files",
                )
            },
            label = { Text("Files") },
            alwaysShowLabel = true,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = TextPrimary,
                selectedTextColor = TextPrimary,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextTertiary,
                indicatorColor = NetflixRed.copy(alpha = 0.26f),
            ),
        )

        NavigationRailItem(
            selected = selectedTab == ConnectedDashboardTab.Library,
            onClick = { onTabSelected(ConnectedDashboardTab.Library) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Library",
                )
            },
            label = { Text("Library") },
            alwaysShowLabel = true,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = TextPrimary,
                selectedTextColor = TextPrimary,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextTertiary,
                indicatorColor = NetflixRed.copy(alpha = 0.26f),
            ),
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationRailItem(
            selected = selectedTab == ConnectedDashboardTab.Settings,
            onClick = { onTabSelected(ConnectedDashboardTab.Settings) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                )
            },
            label = { Text("Settings") },
            alwaysShowLabel = true,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = TextPrimary,
                selectedTextColor = TextPrimary,
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextTertiary,
                indicatorColor = NetflixRed.copy(alpha = 0.26f),
            ),
        )
    }
}

@Composable
private fun DashboardPreviewPanel(
    previewItem: DashboardPreviewItem?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewScrollState = rememberScrollState()
    val previewMetadata = rememberPreviewMetadata(previewItem)
    val resolvedTitle = previewMetadata?.title?.takeIf { it.isNotBlank() } ?: previewItem?.title

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(previewScrollState)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            if (previewItem == null) {
                EmptyState(
                    icon = Icons.Filled.Movie,
                    message = "Item details appear here",
                )
            } else {
                val previewSpec = previewItem.previewSpec
                if (previewSpec != null) {
                    val requestBuilder = ImageRequest.Builder(context)
                        .data(previewSpec.model)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)

                    if (!previewSpec.diskCacheKey.isNullOrBlank()) {
                        requestBuilder.diskCacheKey(previewSpec.diskCacheKey)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .background(DividerGray.copy(alpha = 0.30f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = requestBuilder.build(),
                            imageLoader = ThumbnailImageLoaderProvider.get(context),
                            contentDescription = "Preview image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(DividerGray.copy(alpha = 0.30f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = resolvedTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = previewItem.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                previewMetadata?.takeIf { it.hasStructuredPreviewRows() }?.let { metadata ->
                    Spacer(modifier = Modifier.height(16.dp))
                    PreviewMetadataTable(
                        metadata = metadata,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberPreviewMetadata(previewItem: DashboardPreviewItem?): JvrMovieMetadata? {
    val context = LocalContext.current.applicationContext
    val lookupRequest = previewItem?.metadataLookupRequest
    val cachedMetadata = remember(
        context,
        lookupRequest?.code,
        lookupRequest?.folderPath,
    ) {
        if (lookupRequest == null) {
            null
        } else {
            JvrLibraryMetadataProvider.peekCached(
                context = context,
                rawCode = lookupRequest.code,
                folderPath = lookupRequest.folderPath,
            )
        }
    }

    val metadataState = produceState<JvrMovieMetadata?>(
        initialValue = previewItem?.metadata ?: cachedMetadata,
        key1 = previewItem?.key,
        key2 = lookupRequest?.code,
        key3 = lookupRequest?.folderPath,
    ) {
        value = previewItem?.metadata ?: cachedMetadata

        if (previewItem == null || lookupRequest == null) {
            return@produceState
        }

        val resolvedMetadata = JvrLibraryMetadataProvider.getByCode(
            context = context,
            rawCode = lookupRequest.code,
            folderPath = lookupRequest.folderPath,
        )

        if (resolvedMetadata != null) {
            value = resolvedMetadata
        }
    }

    return metadataState.value
}

@Composable
private fun PreviewMetadataTable(
    metadata: JvrMovieMetadata,
    modifier: Modifier = Modifier,
) {
    val labelWidth = 112.dp

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (metadata.casts.isNotEmpty()) {
            PreviewMetadataRow(
                label = "Name",
                labelWidth = labelWidth,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    metadata.casts.forEach { cast ->
                        PreviewCastValue(cast = cast)
                    }
                }
            }
        }

        metadata.studio?.takeIf { it.isNotBlank() }?.let { studio ->
            PreviewMetadataRow(
                label = "Studio",
                labelWidth = labelWidth,
            ) {
                Text(
                    text = studio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }

        metadata.releaseDate?.let { releaseDate ->
            PreviewMetadataRow(
                label = "Release Date",
                labelWidth = labelWidth,
            ) {
                Text(
                    text = releaseDate.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }

        if (metadata.genres.isNotEmpty()) {
            PreviewMetadataRow(
                label = "Genres",
                labelWidth = labelWidth,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    metadata.genres.forEach { genre ->
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMetadataRow(
    label: String,
    labelWidth: Dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Box(
            modifier = Modifier.weight(1f),
        ) {
            content()
        }
    }
}

@Composable
private fun PreviewCastValue(cast: JvrCastMetadata) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!cast.profileImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = cast.profileImageUrl,
                imageLoader = imageLoader,
                contentDescription = "${cast.englishName} profile image",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DividerGray.copy(alpha = 0.35f)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DividerGray.copy(alpha = 0.35f)),
            )
        }

        Text(
            text = formatPreviewCastLabel(cast),
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
    }
}

private fun formatPreviewCastLabel(cast: JvrCastMetadata): String {
    val japaneseName = cast.japaneseName?.takeIf { it.isNotBlank() }
    val englishName = cast.englishName.takeIf { it.isNotBlank() }
    val hasDistinctEnglishName =
        englishName != null &&
                japaneseName != null &&
                englishName.any { it in 'A'..'Z' || it in 'a'..'z' } &&
                !englishName.equals(japaneseName, ignoreCase = true)

    return when {
        japaneseName != null && hasDistinctEnglishName -> "$japaneseName ($englishName)"
        japaneseName != null -> japaneseName
        englishName != null -> englishName
        else -> ""
    }
}

private fun JvrMovieMetadata.hasStructuredPreviewRows(): Boolean {
    return casts.isNotEmpty() ||
            !studio.isNullOrBlank() ||
            releaseDate != null ||
            genres.isNotEmpty()
}

private fun openPreviewSelectionIfFocused(
    currentPreviewKey: String?,
    previewItem: DashboardPreviewItem,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
) {
    if (currentPreviewKey == previewItem.key) {
        previewItem.onOpen?.invoke()
    } else {
        onPreviewFocused(previewItem)
    }
}

@Composable
private fun ConnectedPaneResizeHandle(
    onDragDeltaPx: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .width(18.dp)
            .hoverable(interactionSource = interactionSource)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState(onDelta = onDragDeltaPx),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(96.dp)
                .background(
                    color = if (isHovered || isPressed) {
                        NetflixRed.copy(alpha = 0.82f)
                    } else {
                        DividerGray.copy(alpha = 0.92f)
                    },
                    shape = RoundedCornerShape(999.dp),
                )
        )
    }
}

@Composable
private fun ConnectionStatusChip(isConnected: Boolean) {
    val containerColor = if (isConnected) SuccessGreen else CardBackground
    val contentColor = if (isConnected) StreamingBlack else TextSecondary

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (isConnected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.Cloud
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (isConnected) "Connected" else "Offline",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

private fun resolveAppVersionLabel(context: Context): String {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
        packageInfo.versionName?.takeIf { it.isNotBlank() } ?: "Unknown"
    }.getOrElse { "Unknown" }
}

private fun deleteTrackedThumbnailFiles(directory: File) {
    if (!directory.exists()) return

    if (!directory.deleteRecursively()) {
        throw IllegalStateException("Failed to delete ${directory.absolutePath}")
    }
}

private suspend fun resetThumbnailState(context: Context) =
    withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val thumbnailImageLoader = ThumbnailImageLoaderProvider.get(appContext)

        thumbnailImageLoader.memoryCache?.clear()
        thumbnailImageLoader.diskCache?.clear()

        deleteTrackedThumbnailFiles(File(appContext.filesDir, "thumbnails"))
        deleteTrackedThumbnailFiles(File(appContext.filesDir, "group_posters"))

        val database = blackark.app.vr.data.database.AppDatabase.getDatabase(appContext)
        database.videoDao().clearAllThumbnailPaths()
        database.virtualGroupMetadataDao().clearAll()
        JvrLibraryMetadataProvider.clearMemoryCaches()
    }

@Composable
private fun SettingsPanel(
    versionLabel: String,
    isResettingThumbnails: Boolean,
    resetErrorMessage: String?,
    onResetThumbnails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CardBackground,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Reset cache",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )

                Button(
                    onClick = onResetThumbnails,
                    enabled = !isResettingThumbnails,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                ) {
                    Text(
                        text = if (isResettingThumbnails) "Resetting..." else "Reset",
                        color = TextPrimary,
                    )
                }
            }

            if (!resetErrorMessage.isNullOrBlank()) {
                Text(
                    text = resetErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    navController: NavController,
    viewModel: MainDashboardViewModel
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentVideos by viewModel.recentVideos.collectAsStateWithLifecycle()

    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<SavedServer?>(null) }
    var compactConnectedTab by remember { mutableStateOf(ConnectedDashboardTab.Files) }
    val previewResetKey = when (compactConnectedTab) {
        ConnectedDashboardTab.Files -> "files:${uiState.selectedServer?.id}:${uiState.currentPath}"
        ConnectedDashboardTab.Library -> "library:${uiState.selectedServer?.id}"
        ConnectedDashboardTab.Settings -> "settings:${uiState.selectedServer?.id}"
    }
    var dashboardPreviewItem by remember(previewResetKey) {
        mutableStateOf<DashboardPreviewItem?>(
            null
        )
    }
    var connectedPreviewWidthOverrideDp by rememberSaveable { mutableStateOf<Float?>(null) }
    var isResettingThumbnails by remember { mutableStateOf(false) }
    var showThumbnailResetCompleteDialog by remember { mutableStateOf(false) }
    var thumbnailResetErrorMessage by remember { mutableStateOf<String?>(null) }
    val dashboardScope = rememberCoroutineScope()
    val appVersionLabel = remember(context) { resolveAppVersionLabel(context) }

    val quickSwitchServers = remember(servers) {
        servers
            .sortedByDescending { it.lastConnected }
            .take(6)
    }
    val density = LocalDensity.current
    var dashboardPanelWidth by remember { mutableStateOf(1920.dp) }
    var dashboardPanelHeight by remember { mutableStateOf(1080.dp) }

    Subspace {
        SpatialMainPanel(
            modifier = SubspaceModifier
                .width(dashboardPanelWidth)
                .height(dashboardPanelHeight),
            dragPolicy = MovePolicy(),
            resizePolicy = ResizePolicy(
                minimumSize = DpVolumeSize(
                    width = 1024.dp,
                    height = 640.dp,
                    depth = 0.dp,
                ),
                onSizeChange = { newSize ->
                    if (newSize.width > 0 && newSize.height > 0) {
                        dashboardPanelWidth = with(density) { newSize.width.toDp() }
                        dashboardPanelHeight = with(density) { newSize.height.toDp() }
                    }
                    true
                }
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                if (!uiState.isConnected) {
                    val panelWidth = if (maxWidth > 920.dp) 760.dp else maxWidth - 24.dp

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))

                        ServerListPanel(
                            servers = servers,
                            selectedServer = uiState.selectedServer,
                            isConnecting = uiState.isConnecting,
                            onServerClick = { server ->
                                compactConnectedTab = ConnectedDashboardTab.Files
                                viewModel.connectToServer(server)
                            },
                            onServerEdit = { server ->
                                serverToEdit = server
                            },
                            onServerDelete = { server ->
                                viewModel.deleteServer(server)
                            },
                            onAddServerClick = {
                                showAddServerDialog = true
                            },
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight(0.78f)
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DashboardOrbitArea(
                            isConnected = true,
                            selectedServer = uiState.selectedServer,
                            registeredServers = quickSwitchServers,
                            onBackToServerSelection = {
                                dashboardPreviewItem = null
                                compactConnectedTab = ConnectedDashboardTab.Files
                                viewModel.disconnect()
                            },
                            onSelectServer = { server ->
                                dashboardPreviewItem = null
                                compactConnectedTab = ConnectedDashboardTab.Files
                                viewModel.connectToServer(server)
                            },
                            onAddServerClick = {
                                showAddServerDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxSize()) {
                            ConnectedSectionRail(
                                selectedTab = compactConnectedTab,
                                onTabSelected = { compactConnectedTab = it },
                                modifier = Modifier.fillMaxHeight(),
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            if (compactConnectedTab == ConnectedDashboardTab.Settings) {
                                SettingsPanel(
                                    versionLabel = appVersionLabel,
                                    isResettingThumbnails = isResettingThumbnails,
                                    resetErrorMessage = thumbnailResetErrorMessage,
                                    onResetThumbnails = {
                                        if (isResettingThumbnails) return@SettingsPanel

                                        isResettingThumbnails = true
                                        showThumbnailResetCompleteDialog = false
                                        thumbnailResetErrorMessage = null
                                        dashboardScope.launch {
                                            runCatching { resetThumbnailState(context) }
                                                .onSuccess {
                                                    dashboardPreviewItem = null
                                                    showThumbnailResetCompleteDialog = true
                                                }
                                                .onFailure { throwable ->
                                                    thumbnailResetErrorMessage =
                                                        "Reset failed: ${throwable.message ?: "Unknown error"}"
                                                }
                                            isResettingThumbnails = false
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            } else {
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    val previewDensity = LocalDensity.current
                                    val defaultPreviewWidth = when {
                                        maxWidth >= 1500.dp -> 420.dp
                                        maxWidth >= 1180.dp -> 360.dp
                                        else -> 300.dp
                                    }
                                    val minPreviewWidth = 280.dp
                                    val minPrimaryPaneWidth = when (compactConnectedTab) {
                                        ConnectedDashboardTab.Files -> 460.dp
                                        ConnectedDashboardTab.Library -> 420.dp
                                        ConnectedDashboardTab.Settings -> 420.dp
                                    }
                                    val dividerWidth = 18.dp
                                    val maxPreviewWidth =
                                        (maxWidth - dividerWidth - minPrimaryPaneWidth)
                                            .coerceAtLeast(minPreviewWidth)

                                    LaunchedEffect(
                                        defaultPreviewWidth,
                                        minPreviewWidth,
                                        maxPreviewWidth,
                                    ) {
                                        val currentWidth =
                                            connectedPreviewWidthOverrideDp
                                                ?: defaultPreviewWidth.value
                                        val clampedWidth = currentWidth.coerceIn(
                                            minPreviewWidth.value,
                                            maxPreviewWidth.value,
                                        )
                                        if (connectedPreviewWidthOverrideDp != clampedWidth) {
                                            connectedPreviewWidthOverrideDp = clampedWidth
                                        }
                                    }

                                    val previewWidth = (
                                            connectedPreviewWidthOverrideDp
                                                ?: defaultPreviewWidth.value
                                            ).coerceIn(
                                            minPreviewWidth.value,
                                            maxPreviewWidth.value,
                                        ).dp

                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                        ) {
                                            if (compactConnectedTab == ConnectedDashboardTab.Files) {
                                                FileBrowserPanel(
                                                    files = files,
                                                    recentVideos = recentVideos,
                                                    currentPath = uiState.currentPath,
                                                    isConnected = uiState.isConnected,
                                                    isLoading = uiState.isLoadingFiles,
                                                    errorMessage = uiState.errorMessage,
                                                    viewMode = uiState.fileViewMode,
                                                    currentPreviewKey = dashboardPreviewItem?.key,
                                                    onFileClick = { file ->
                                                        dashboardPreviewItem = null
                                                        viewModel.navigateToFile(file)
                                                    },
                                                    onBackClick = {
                                                        dashboardPreviewItem = null
                                                        viewModel.navigateBack()
                                                    },
                                                    onToggleViewMode = {
                                                        dashboardPreviewItem = null
                                                        viewModel.toggleFileViewMode()
                                                    },
                                                    onDeleteFiles = { selectedFiles ->
                                                        viewModel.deleteFiles(selectedFiles)
                                                    },
                                                    onPlayVideo = { filePath, fileName ->
                                                        dashboardPreviewItem = null
                                                        navController.navigate(
                                                            Screen.VideoPlayer.createRoute(
                                                                filePath,
                                                                fileName
                                                            )
                                                        )
                                                    },
                                                    onFavoriteToggle = { file, isFavorite ->
                                                        viewModel.toggleFavoriteForFile(
                                                            file,
                                                            isFavorite
                                                        )
                                                    },
                                                    onPreviewFocused = { preview ->
                                                        dashboardPreviewItem = preview
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                )
                                            } else {
                                                FavoritesPanel(
                                                    favorites = favorites,
                                                    recentVideos = recentVideos,
                                                    isConnected = uiState.isConnected,
                                                    currentPreviewKey = dashboardPreviewItem?.key,
                                                    onFavoriteClick = { video ->
                                                        dashboardPreviewItem = null
                                                        navController.navigate(
                                                            Screen.VideoPlayer.createRoute(
                                                                video.filePath,
                                                                video.fileName
                                                            )
                                                        )
                                                    },
                                                    onRecentClick = { video ->
                                                        dashboardPreviewItem = null
                                                        navController.navigate(
                                                            Screen.VideoPlayer.createRoute(
                                                                video.filePath,
                                                                video.fileName
                                                            )
                                                        )
                                                    },
                                                    onFavoriteToggle = { video ->
                                                        viewModel.toggleFavorite(
                                                            video.id,
                                                            !video.isFavorite
                                                        )
                                                    },
                                                    onPreviewFocused = { preview ->
                                                        dashboardPreviewItem = preview
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .weight(1f)
                                                )
                                            }
                                        }

                                        ConnectedPaneResizeHandle(
                                            onDragDeltaPx = { deltaPx ->
                                                val deltaDp =
                                                    with(previewDensity) { deltaPx.toDp().value }
                                                val currentWidth =
                                                    connectedPreviewWidthOverrideDp
                                                        ?: defaultPreviewWidth.value
                                                connectedPreviewWidthOverrideDp = (
                                                        currentWidth - deltaDp
                                                        ).coerceIn(
                                                        minPreviewWidth.value,
                                                        maxPreviewWidth.value,
                                                    )
                                            },
                                            modifier = Modifier.fillMaxHeight(),
                                        )

                                        DashboardPreviewPanel(
                                            previewItem = dashboardPreviewItem,
                                            modifier = Modifier
                                                .width(previewWidth)
                                                .fillMaxHeight(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!uiState.isConnected) {
            Orbiter(
                position = ContentEdge.Top,
                offset = 66.dp,
                offsetType = OrbiterOffsetType.OuterEdge,
                alignment = Alignment.CenterHorizontally,
                elevation = 24.dp,
                shouldRenderInNonSpatial = true,
            ) {
                DashboardWindowControls(
                    onMinimize = {
                        activity?.moveTaskToBack(true)
                    },
                    onClose = {
                        activity?.finishAndRemoveTask()
                        activity?.finishAffinity()
                    }
                )
            }
        }

        // Add/Edit Server Dialog
        if (showAddServerDialog || serverToEdit != null) {
            AddServerDialog(
                initialServer = serverToEdit,
                defaultUsername = servers
                    .asSequence()
                    .filterNot { it.isLocalStorage }
                    .maxByOrNull { it.lastConnected }
                    ?.username
                    .orEmpty(),
                defaultPassword = servers
                    .asSequence()
                    .filterNot { it.isLocalStorage }
                    .maxByOrNull { it.lastConnected }
                    ?.password
                    .orEmpty(),
                onDismiss = {
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onSave = { server ->
                    if (serverToEdit != null) {
                        viewModel.updateServer(server)
                    } else {
                        viewModel.addServer(server)
                    }
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onTestConnection = { server ->
                    viewModel.testConnection(server)
                }
            )
        }

        // Loading Overlay
        if (uiState.isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to server...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    if (showThumbnailResetCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showThumbnailResetCompleteDialog = false },
            title = {
                Text("Reset complete")
            },
            text = {
                Text("Thumbnail cache has been reset.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showThumbnailResetCompleteDialog = false },
                ) {
                    Text("OK", color = NetflixRed)
                }
            },
        )
    }
}

@Composable
private fun DashboardOrbitArea(
    isConnected: Boolean,
    selectedServer: SavedServer?,
    registeredServers: List<SavedServer>,
    onBackToServerSelection: () -> Unit,
    onSelectServer: (SavedServer) -> Unit,
    onAddServerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showServerMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardBackgroundHover.copy(alpha = 0.95f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 10.dp,
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnected) {
                OrbiterActionButton(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to server selection",
                    onClick = onBackToServerSelection
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isConnected) {
                        selectedServer?.serverName ?: "Connected"
                    } else {
                        "Select Server"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ConnectionStatusChip(isConnected = isConnected)

            Box {
                TextButton(onClick = { showServerMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Servers", color = TextPrimary)
                }

                DropdownMenu(
                    expanded = showServerMenu,
                    onDismissRequest = { showServerMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Local Storage") },
                        onClick = {
                            showServerMenu = false
                            onSelectServer(SavedServer.createLocalStorageServer())
                        },
                        trailingIcon = {
                            if (selectedServer?.isLocalStorage == true) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                )
                            }
                        }
                    )

                    if (registeredServers.isNotEmpty()) {
                        HorizontalDivider(color = DividerGray)
                    }

                    registeredServers.forEach { server ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = server.serverName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                showServerMenu = false
                                onSelectServer(server)
                            },
                            trailingIcon = {
                                if (selectedServer?.id == server.id && selectedServer.isLocalStorage.not()) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                    )
                                }
                            }
                        )
                    }
                }
            }

            OrbiterActionButton(
                imageVector = Icons.Filled.Add,
                contentDescription = "Register server",
                onClick = onAddServerClick
            )
        }
    }
}

@Composable
private fun DashboardWindowControls(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = CardBackgroundHover.copy(alpha = 0.94f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(CardBackground.copy(alpha = 0.75f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(30.dp)
                )
            }

            OrbiterActionButton(
                imageVector = Icons.Filled.Minimize,
                contentDescription = "Minimize",
                onClick = onMinimize
            )

            OrbiterActionButton(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose
            )
        }
    }
}

@Composable
private fun OrbiterActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActivePointer = isHovered || isFocused

    val iconTint = when {
        isPressed -> NetflixRed
        isActivePointer -> NetflixRed.copy(alpha = 0.85f)
        else -> TextPrimary
    }

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(44.dp)
            .hoverable(interactionSource = interactionSource)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private data class VirtualVideoGroup(
    val key: String,
    val files: List<SMBFileItem>
) {
    val fileCount: Int
        get() = files.size

    val representativeFile: SMBFileItem?
        get() = files.firstOrNull()
}

private sealed interface FileBrowserDisplayItem {
    val stableKey: String

    data class Entry(
        val file: SMBFileItem,
        val isVirtualGroupMember: Boolean = false,
    ) : FileBrowserDisplayItem {
        override val stableKey: String = "file:${file.path}"
    }

    data class Group(
        val virtualGroup: VirtualVideoGroup,
    ) : FileBrowserDisplayItem {
        override val stableKey: String = "group:${virtualGroup.key}"
    }
}

private const val GROUP_THUMBNAIL_LOG_TAG = "VirtualGroupThumb"
private const val FILE_THUMBNAIL_LOG_TAG = "VideoFileThumb"

private data class VideoMetadataLookupRequest(
    val code: String,
    val folderPath: String,
)

private val videoFileCodePattern = Regex("(?i)([a-z]{2,10})[-_](\\d{2,5})(?!\\d)")
private val makerYearFolderPattern =
    Regex("(^|/)maker/(?:19|20)\\d{2}(/|$)", RegexOption.IGNORE_CASE)
private val avVrFolderPattern = Regex("(^|/)av/vr(/|$)", RegexOption.IGNORE_CASE)

private fun shouldEnablePosterPopupForPath(path: String): Boolean {
    val normalized = path
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .lowercase()

    return makerYearFolderPattern.containsMatchIn(normalized) ||
            avVrFolderPattern.containsMatchIn(normalized)
}

private fun buildVideoMetadataLookupRequest(filePath: String): VideoMetadataLookupRequest? {
    val normalizedPath = filePath
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .trim()

    if (normalizedPath.isBlank()) return null

    val folderPath = normalizedPath.substringBeforeLast('/', "")

    val fileName = normalizedPath.substringAfterLast('/')
    val stem = fileName.substringBeforeLast('.', fileName)
    val codeMatch = videoFileCodePattern.find(stem) ?: return null

    val code = "${codeMatch.groupValues[1].uppercase()}-${codeMatch.groupValues[2]}"
    return VideoMetadataLookupRequest(code = code, folderPath = folderPath)
}

@Composable
private fun rememberVideoFileMetadata(file: SMBFileItem, isVideoFile: Boolean): JvrMovieMetadata? {
    if (!isVideoFile) return null

    val context = LocalContext.current.applicationContext
    val lookupRequest = remember(file.path) {
        buildVideoMetadataLookupRequest(file.path)
    } ?: return null

    val cachedMetadata = remember(context, lookupRequest.code, lookupRequest.folderPath) {
        JvrLibraryMetadataProvider.peekCached(context, lookupRequest.code, lookupRequest.folderPath)
    }

    val metadataState = produceState<JvrMovieMetadata?>(
        initialValue = cachedMetadata,
        key1 = lookupRequest.code,
        key2 = lookupRequest.folderPath,
    ) {
        if (cachedMetadata != null) {
            Log.d(
                FILE_THUMBNAIL_LOG_TAG,
                "Metadata immediate cache hit for file=${file.path} code=${lookupRequest.code}"
            )
            return@produceState
        }

        Log.d(
            FILE_THUMBNAIL_LOG_TAG,
            "Resolving metadata for file=${file.path} code=${lookupRequest.code} path='${lookupRequest.folderPath}'"
        )

        value = JvrLibraryMetadataProvider.getByCode(
            context = context,
            rawCode = lookupRequest.code,
            folderPath = lookupRequest.folderPath,
        )
    }

    return metadataState.value
}

@Composable
private fun rememberGroupMetadata(groupCode: String, folderPath: String): JvrMovieMetadata? {
    val context = LocalContext.current.applicationContext
    val normalizedCode = remember(groupCode) { groupCode.trim().uppercase() }
    val normalizedFolderPath = remember(folderPath) { folderPath.trim() }
    val cachedMetadata = remember(context, normalizedCode, normalizedFolderPath) {
        JvrLibraryMetadataProvider.peekCached(context, normalizedCode, normalizedFolderPath)
    }

    val metadataState = produceState<JvrMovieMetadata?>(
        initialValue = cachedMetadata,
        key1 = normalizedCode,
        key2 = normalizedFolderPath,
    ) {
        if (cachedMetadata != null) {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "Metadata immediate cache hit for group=$normalizedCode path='$normalizedFolderPath' (title='${cachedMetadata.title}')"
            )
            return@produceState
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Resolving metadata for virtual group code=$normalizedCode path='$normalizedFolderPath'"
        )
        val metadata = JvrLibraryMetadataProvider.getByCode(
            context = context,
            rawCode = normalizedCode,
            folderPath = normalizedFolderPath,
        )

        if (metadata == null) {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "No remote metadata for group=$normalizedCode. Falling back to generated video thumbnail."
            )
        } else {
            Log.d(
                GROUP_THUMBNAIL_LOG_TAG,
                "Metadata loaded for group=$normalizedCode posterUrl=${metadata.posterUrl ?: "<none>"}"
            )
        }

        value = metadata
    }
    return metadataState.value
}

/**
 * LEFT PANEL: Server List
 */
@Composable
private fun ServerListPanel(
    servers: List<SavedServer>,
    selectedServer: SavedServer?,
    isConnecting: Boolean,
    onServerClick: (SavedServer) -> Unit,
    onServerEdit: (SavedServer) -> Unit,
    onServerDelete: (SavedServer) -> Unit,
    onAddServerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CardBackgroundHover
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, DividerGray.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onAddServerClick,
                    enabled = !isConnecting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Server",
                        tint = NetflixRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // Server List with Local Storage option
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Storage card (always first)
                item {
                    LocalStorageCard(
                        onClick = { onServerClick(SavedServer.createLocalStorageServer()) },
                        isSelected = selectedServer?.isLocalStorage == true,
                        isConnecting = isConnecting
                    )
                }

                // SMB servers
                if (servers.isNotEmpty()) {
                    items(servers) { server ->
                        FancyServerCard(
                            server = server,
                            onClick = { onServerClick(server) },
                            onEdit = { onServerEdit(server) },
                            onDelete = { onServerDelete(server) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * CENTER PANEL: File Browser
 */
@Composable
private fun FileBrowserPanel(
    files: List<SMBFileItem>,
    recentVideos: List<RecentVideo>,
    currentPath: String,
    isConnected: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    viewMode: FileBrowserViewMode,
    currentPreviewKey: String?,
    onFileClick: (SMBFileItem) -> Unit,
    onBackClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onDeleteFiles: suspend (List<SMBFileItem>) -> Result<Int>,
    onPlayVideo: (String, String) -> Unit,
    onFavoriteToggle: (SMBFileItem, Boolean) -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val thumbnailImageLoader = remember(context) { ThumbnailImageLoaderProvider.get(context) }
    val thumbnailPrefetchCoroutineContext = remember { Dispatchers.IO.limitedParallelism(1) }
    val scope = rememberCoroutineScope()
    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleteInProgress by remember { mutableStateOf(false) }
    var activeVirtualGroupKey by remember(currentPath) { mutableStateOf<String?>(null) }

    val virtualGroups = remember(files) { buildVirtualVideoGroups(files) }
    val activeVirtualGroup = remember(virtualGroups, activeVirtualGroupKey) {
        activeVirtualGroupKey?.let(virtualGroups::get)
    }
    val displayItems = remember(files, virtualGroups, activeVirtualGroupKey) {
        buildFileBrowserDisplayItems(files, virtualGroups, activeVirtualGroupKey)
    }
    val virtualGroupPrefetchPaths = remember(virtualGroups, activeVirtualGroupKey) {
        if (activeVirtualGroupKey != null) {
            emptyList()
        } else {
            virtualGroups.values
                .asSequence()
                .flatMap { group -> group.files.asSequence() }
                .map { file -> file.path }
                .distinct()
                .toList()
        }
    }
    val displayedPath = remember(currentPath, activeVirtualGroup) {
        activeVirtualGroup?.let { group ->
            if (currentPath.isBlank() || currentPath == "/") {
                group.key
            } else {
                "$currentPath/${group.key}"
            }
        } ?: currentPath
    }

    LaunchedEffect(displayItems, isDeleteMode) {
        selectedPaths = if (isDeleteMode) {
            val currentPaths = displayItems
                .mapNotNull { item ->
                    when (item) {
                        is FileBrowserDisplayItem.Entry -> item.file.path
                        is FileBrowserDisplayItem.Group -> null
                    }
                }
                .toSet()
            selectedPaths.filter { it in currentPaths }.toSet()
        } else {
            emptySet()
        }
    }

    LaunchedEffect(virtualGroups, activeVirtualGroupKey) {
        if (activeVirtualGroupKey != null && activeVirtualGroupKey !in virtualGroups.keys) {
            activeVirtualGroupKey = null
        }
    }
    DisposableEffect(
        isConnected,
        isLoading,
        errorMessage,
        currentPath,
        activeVirtualGroupKey,
        virtualGroupPrefetchPaths,
    ) {
        if (
            !isConnected ||
            isLoading ||
            errorMessage != null ||
            activeVirtualGroupKey != null ||
            virtualGroupPrefetchPaths.isEmpty()
        ) {
            onDispose {}
        } else {
            Log.d(
                FILE_THUMBNAIL_LOG_TAG,
                "Prefetching extracted thumbnails for ${virtualGroupPrefetchPaths.size} grouped files in path='$currentPath'",
            )

            val disposables = mutableListOf<Disposable>()

            virtualGroupPrefetchPaths.forEach { filePath ->
                val diskCacheKey =
                    VideoThumbnailFetcher.diskCacheKey(
                        path = filePath,
                        allowMetadataPoster = false,
                    )
                val snapshot = thumbnailImageLoader.diskCache?.openSnapshot(diskCacheKey)
                val isCached = snapshot != null
                snapshot?.close()
                if (isCached) {
                    return@forEach
                }

                val disposable =
                    thumbnailImageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(
                                VideoThumbnailFetcher.Model(
                                    path = filePath,
                                    allowMetadataPoster = false,
                                )
                            )
                            .diskCacheKey(diskCacheKey)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .fetcherCoroutineContext(thumbnailPrefetchCoroutineContext)
                            .decoderCoroutineContext(thumbnailPrefetchCoroutineContext)
                            .build()
                    )
                disposables += disposable
            }

            onDispose {
                disposables.forEach { disposable ->
                    if (!disposable.isDisposed) {
                        disposable.dispose()
                    }
                }
                if (disposables.isNotEmpty()) {
                    Log.d(
                        FILE_THUMBNAIL_LOG_TAG,
                        "Cancelled ${disposables.size} grouped thumbnail prefetch requests for path='$currentPath'",
                    )
                }
            }
        }
    }
    LaunchedEffect(
        isConnected,
        isLoading,
        errorMessage,
        displayItems,
        isDeleteMode,
        activeVirtualGroupKey,
        viewMode,
    ) {
        if (!isConnected || isLoading || errorMessage != null || displayItems.isEmpty() || isDeleteMode) {
            onPreviewFocused(null)
        }
    }

    fun toggleSelection(file: SMBFileItem) {
        if (file.isDirectory) return
        selectedPaths = if (selectedPaths.contains(file.path)) {
            selectedPaths - file.path
        } else {
            selectedPaths + file.path
        }
    }

    fun openVirtualGroup(groupKey: String) {
        onPreviewFocused(null)
        activeVirtualGroupKey = groupKey
        selectedPaths = emptySet()
        isDeleteMode = false
    }

    fun handleBackAction() {
        if (activeVirtualGroupKey != null) {
            onPreviewFocused(null)
            activeVirtualGroupKey = null
            selectedPaths = emptySet()
            isDeleteMode = false
            return
        }
        onPreviewFocused(null)
        onBackClick()
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    when {
                        isDeleteMode -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${selectedPaths.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedPaths.isEmpty()) TextTertiary else NetflixRed,
                                maxLines = 1
                            )
                        }

                        isConnected && displayedPath.isNotEmpty() -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleViewMode,
                        enabled = isConnected && !isLoading && !isDeleteMode && !isDeleteInProgress
                    ) {
                        Icon(
                            imageVector = if (viewMode == FileBrowserViewMode.List) {
                                Icons.Filled.ViewModule
                            } else {
                                Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = if (viewMode == FileBrowserViewMode.List) {
                                "Switch to thumbnail view"
                            } else {
                                "Switch to list view"
                            },
                            tint = if (isDeleteMode) TextTertiary else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isDeleteMode) {
                                isDeleteMode = true
                                selectedPaths = emptySet()
                                return@IconButton
                            }

                            if (selectedPaths.isEmpty()) {
                                isDeleteMode = false
                                return@IconButton
                            }

                            showDeleteConfirmDialog = true
                        },
                        enabled = isConnected && !isLoading && !isDeleteInProgress
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = if (isDeleteMode) {
                                "Confirm selected file deletion"
                            } else {
                                "Select files to delete"
                            },
                            tint = if (isDeleteMode) NetflixRed else TextSecondary
                        )
                    }

                    if ((activeVirtualGroup != null || (currentPath.isNotEmpty() && currentPath != "/")) && !isDeleteMode) {
                        IconButton(onClick = ::handleBackAction) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // Content
            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Filled.Cloud,
                        message = "Connect to a server to browse files"
                    )
                }

                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = NetflixRed,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                displayItems.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.FolderOpen,
                        message = "No files in this directory"
                    )
                }

                else -> {
                    when (viewMode) {
                        FileBrowserViewMode.List -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    item.virtualGroup.key,
                                                    currentPath
                                                )
                                            VirtualGroupListCard(
                                                group = item.virtualGroup,
                                                metadata = groupMetadata,
                                                currentPreviewKey = currentPreviewKey,
                                                onClick = {},
                                                onPopupClick = {
                                                    openVirtualGroup(item.virtualGroup.key)
                                                },
                                                onPreviewFocused = onPreviewFocused,
                                            )
                                        }

                                        is FileBrowserDisplayItem.Entry -> {
                                            val file = item.file
                                            val isVideoFile =
                                                !file.isDirectory && SMBClient.isVideoFile(file.name)
                                            val videoInDb =
                                                recentVideos.find { it.filePath == file.path }
                                            val isFavorite = videoInDb?.isFavorite ?: false
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileListEntryCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                allowJvrMetadata = !item.isVirtualGroupMember,
                                                isFavorite = isFavorite,
                                                isSelectionMode = isDeleteMode,
                                                isSelected = isSelected,
                                                currentPreviewKey = currentPreviewKey,
                                                onFavoriteToggle = if (isDeleteMode || !isVideoFile) {
                                                    null
                                                } else {
                                                    { onFavoriteToggle(file, isFavorite) }
                                                },
                                                onClick = {
                                                    if (isDeleteMode) {
                                                        toggleSelection(file)
                                                    } else if (!isVideoFile) {
                                                        onFileClick(file)
                                                    }
                                                },
                                                onPopupClick = {
                                                    if (isVideoFile) {
                                                        onPlayVideo(file.path, file.name)
                                                    } else {
                                                        onFileClick(file)
                                                    }
                                                },
                                                onPreviewFocused = onPreviewFocused,
                                                modifier = Modifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        FileBrowserViewMode.Thumbnail -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    item.virtualGroup.key,
                                                    currentPath
                                                )
                                            VirtualGroupThumbnailCard(
                                                group = item.virtualGroup,
                                                metadata = groupMetadata,
                                                currentPreviewKey = currentPreviewKey,
                                                onClick = {},
                                                onPopupClick = {
                                                    openVirtualGroup(item.virtualGroup.key)
                                                },
                                                onPreviewFocused = onPreviewFocused,
                                            )
                                        }

                                        is FileBrowserDisplayItem.Entry -> {
                                            val file = item.file
                                            val isVideoFile =
                                                !file.isDirectory && SMBClient.isVideoFile(file.name)
                                            val videoInDb =
                                                recentVideos.find { it.filePath == file.path }
                                            val isFavorite = videoInDb?.isFavorite ?: false
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileThumbnailCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                allowJvrMetadata = !item.isVirtualGroupMember,
                                                isFavorite = isFavorite,
                                                isSelectionMode = isDeleteMode,
                                                isSelected = isSelected,
                                                currentPreviewKey = currentPreviewKey,
                                                onFavoriteToggle = if (isDeleteMode || !isVideoFile) {
                                                    null
                                                } else {
                                                    { onFavoriteToggle(file, isFavorite) }
                                                },
                                                onClick = {
                                                    if (isDeleteMode) {
                                                        toggleSelection(file)
                                                    } else if (!isVideoFile) {
                                                        onFileClick(file)
                                                    }
                                                },
                                                onPopupClick = {
                                                    if (isVideoFile) {
                                                        onPlayVideo(file.path, file.name)
                                                    } else {
                                                        onFileClick(file)
                                                    }
                                                },
                                                onPreviewFocused = onPreviewFocused,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleteInProgress) {
                    showDeleteConfirmDialog = false
                }
            },
            title = {
                Text("Delete selected files?")
            },
            text = {
                Text("This will permanently delete ${selectedPaths.size} file(s).")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets =
                            files.filter { !it.isDirectory && selectedPaths.contains(it.path) }
                        if (targets.isEmpty()) {
                            isDeleteMode = false
                            selectedPaths = emptySet()
                            showDeleteConfirmDialog = false
                            return@TextButton
                        }

                        scope.launch {
                            isDeleteInProgress = true
                            val result = onDeleteFiles(targets)
                            isDeleteInProgress = false
                            showDeleteConfirmDialog = false

                            if (result.isSuccess) {
                                isDeleteMode = false
                                selectedPaths = emptySet()
                            }
                        }
                    },
                    enabled = !isDeleteInProgress
                ) {
                    if (isDeleteInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = NetflixRed
                        )
                    } else {
                        Text("Delete", color = NetflixRed)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleteInProgress
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private data class GroupHoverPreviewSpec(
    val model: Any,
    val diskCacheKey: String?,
    val source: String,
)

private fun shouldUseMetadataForIndividualVideo(fileName: String): Boolean {
    return extractVirtualGroupKey(fileName) == null
}

private fun buildGroupHoverPreviewSpec(
    shouldUsePoster: Boolean,
    posterUrl: String?,
    posterCacheKey: String?,
    representativePath: String?,
    allowMetadataPosterForGeneratedThumbnail: Boolean = true,
): GroupHoverPreviewSpec? {
    return when {
        shouldUsePoster && !posterUrl.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = posterUrl,
            diskCacheKey = posterCacheKey,
            source = "poster",
        )

        !representativePath.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = VideoThumbnailFetcher.Model(
                representativePath,
                allowMetadataPoster = allowMetadataPosterForGeneratedThumbnail,
            ),
            diskCacheKey = VideoThumbnailFetcher.diskCacheKey(
                representativePath,
                allowMetadataPoster = allowMetadataPosterForGeneratedThumbnail,
            ),
            source = "generated",
        )

        else -> null
    }
}


private fun buildLibraryPreviewSpec(
    video: RecentVideo,
    allowMetadataPosterForGeneratedThumbnail: Boolean = true,
): GroupHoverPreviewSpec? {
    return when {
        allowMetadataPosterForGeneratedThumbnail && !video.thumbnailPath.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = video.thumbnailPath,
            diskCacheKey = video.thumbnailPath,
            source = "cached",
        )

        video.filePath.isNotBlank() -> GroupHoverPreviewSpec(
            model = VideoThumbnailFetcher.Model(
                video.filePath,
                allowMetadataPoster = allowMetadataPosterForGeneratedThumbnail,
            ),
            diskCacheKey = VideoThumbnailFetcher.diskCacheKey(
                video.filePath,
                allowMetadataPoster = allowMetadataPosterForGeneratedThumbnail,
            ),
            source = "generated",
        )

        else -> null
    }
}

private class FileAnchoredPopupPositionProvider(
    private val anchorBoundsOverride: IntRect?,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val effectiveAnchor = anchorBoundsOverride ?: anchorBounds
        val horizontalMargin = 16
        val verticalMargin = 16
        val popupGap = 10

        val maxX = (windowSize.width - popupContentSize.width - horizontalMargin)
            .coerceAtLeast(horizontalMargin)
        val maxY = (windowSize.height - popupContentSize.height - verticalMargin)
            .coerceAtLeast(verticalMargin)

        val anchorCenterX = (effectiveAnchor.left + effectiveAnchor.right) / 2
        val centeredX = anchorCenterX - popupContentSize.width / 2
        val resolvedX = centeredX.coerceIn(horizontalMargin, maxX)

        val preferAbove = effectiveAnchor.top - popupContentSize.height - popupGap
        val preferBelow = effectiveAnchor.bottom + popupGap
        val resolvedY = when {
            preferAbove >= verticalMargin -> preferAbove
            preferBelow <= maxY -> preferBelow
            else -> preferAbove
        }.coerceIn(verticalMargin, maxY)

        return IntOffset(
            x = resolvedX,
            y = resolvedY,
        )
    }
}

@Composable
private fun GroupHoverPreviewPopup(
    show: Boolean,
    groupKey: String,
    title: String,
    previewSpec: GroupHoverPreviewSpec?,
    anchorBounds: IntRect? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
) {
    if (!show || previewSpec == null) return

    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)
    val imageRequest = remember(previewSpec.model, previewSpec.diskCacheKey) {
        ImageRequest.Builder(context)
            .data(previewSpec.model)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .apply {
                if (!previewSpec.diskCacheKey.isNullOrBlank()) {
                    diskCacheKey(previewSpec.diskCacheKey)
                }
            }
            .build()
    }
    val previewPainter = rememberAsyncImagePainter(
        model = imageRequest,
        imageLoader = imageLoader,
    )
    val intrinsicSize = previewPainter.intrinsicSize
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val intrinsicWidthPx = remember(intrinsicSize) {
        intrinsicSize.width.takeIf { it.isFinite() && it > 0f } ?: 800f
    }
    val intrinsicHeightPx = remember(intrinsicSize) {
        intrinsicSize.height.takeIf { it.isFinite() && it > 0f } ?: 540f
    }
    val isSmallPreviewImage = intrinsicWidthPx <= 1000f && intrinsicHeightPx <= 700f
    val targetScale = if (isSmallPreviewImage) 1.5f else 1f
    val maxPreviewWidthPx = with(density) { (configuration.screenWidthDp.dp * 0.72f).toPx() }
    val maxPreviewHeightPx = with(density) { (configuration.screenHeightDp.dp * 0.70f).toPx() }
    val previewScale = remember(
        intrinsicWidthPx,
        intrinsicHeightPx,
        targetScale,
        maxPreviewWidthPx,
        maxPreviewHeightPx,
    ) {
        minOf(
            targetScale,
            maxPreviewWidthPx / intrinsicWidthPx,
            maxPreviewHeightPx / intrinsicHeightPx,
        )
    }
    val previewWidthDp = with(density) { (intrinsicWidthPx * previewScale).toDp() }
    val previewHeightDp = with(density) { (intrinsicHeightPx * previewScale).toDp() }
    val popupWidth = (previewWidthDp + 24.dp).coerceAtLeast(220.dp)
    val textMaxWidth = (previewWidthDp + 6.dp).coerceAtLeast(196.dp)

    val popupPositionProvider = remember(anchorBounds) {
        FileAnchoredPopupPositionProvider(anchorBounds)
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.96f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NetflixRed.copy(alpha = 0.45f)),
            modifier = Modifier
                .width(popupWidth)
                .padding(8.dp)
                .let { base ->
                    if (interactionSource != null) {
                        base.hoverable(interactionSource = interactionSource)
                    } else {
                        base
                    }
                }
                .let { base ->
                    if (onClick != null) {
                        base.clickable(onClick = onClick)
                    } else {
                        base
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(previewWidthDp)
                        .height(previewHeightDp)
                        .background(DividerGray.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = previewPainter,
                        contentDescription = "Expanded poster preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(textMaxWidth),
                )
                Text(
                    text = "$groupKey · ${previewSpec.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(textMaxWidth),
                )
            }
        }
    }

}

@Composable
private fun VirtualGroupListCard(
    group: VirtualVideoGroup,
    metadata: JvrMovieMetadata?,
    currentPreviewKey: String? = null,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }

    val posterUrl = metadata?.posterUrl
    val representativePath = group.representativeFile?.path
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }
    val previewMetadataLookupRequest = remember(representativePath) {
        representativePath?.let(::buildVideoMetadataLookupRequest)
    }

    var posterLoadFailed by remember(group.key, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = !posterUrl.isNullOrBlank() && !posterLoadFailed
    val isPosterPopupEligible = remember(representativePath) {
        representativePath?.let(::shouldEnablePosterPopupForPath) == true
    }
    val previewSpec = remember(
        shouldUsePoster,
        posterUrl,
        posterCacheKey,
        representativePath,
        isPosterPopupEligible,
    ) {
        if (!isPosterPopupEligible) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = representativePath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        secondaryText,
        previewSpec,
        metadata,
        previewMetadataLookupRequest,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "group:${representativePath ?: group.key}",
            title = displayTitle,
            subtitle = secondaryText,
            previewSpec = previewSpec,
            metadata = metadata,
            metadataLookupRequest = previewMetadataLookupRequest,
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key

    LaunchedEffect(group.key, posterUrl, representativePath, posterLoadFailed) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            !representativePath.isNullOrBlank() -> "generated-video-frame"
            else -> "none"
        }

        val posterCached = if (!posterCacheKey.isNullOrBlank()) {
            val snapshot = imageLoader.diskCache?.openSnapshot(posterCacheKey)
            val exists = snapshot != null
            snapshot?.close()
            exists
        } else {
            false
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Render group=${group.key} source=$source posterFailed=$posterLoadFailed posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
        )
    }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardHovered by cardInteractionSource.collectIsHoveredAsState()
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    var popupAnchorBounds by remember(group.key) { mutableStateOf<IntRect?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource = cardInteractionSource)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isPreviewFocused) {
                        onPopupClick()
                    } else {
                        onPreviewFocused(previewItem)
                        onClick()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isPreviewFocused || isCardHovered || isCardFocused) {
            BorderStroke(1.dp, NetflixRed.copy(alpha = 0.7f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .background(DividerGray)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        popupAnchorBounds = IntRect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt(),
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    shouldUsePoster -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .diskCacheKey(posterCacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load success for group=${group.key}, cacheKey=${posterCacheKey ?: "<none>"}"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.w(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load failed for group=${group.key}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                        )
                                        posterLoadFailed = true
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(VideoThumbnailFetcher.Model(representativePath))
                                .diskCacheKey(VideoThumbnailFetcher.diskCacheKey(representativePath))
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail success for group=${group.key}, path=$representativePath"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.e(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail failed for group=${group.key}, path=$representativePath, reason=${result.throwable.message}"
                                        )
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Generated video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open grouped files",
                tint = TextSecondary
            )
        }

        GroupHoverPreviewPopup(
            show = false,
            groupKey = group.key,
            title = displayTitle,
            previewSpec = previewSpec,
            anchorBounds = popupAnchorBounds,
            onClick = onPopupClick,
        )
    }
}

@Composable
private fun VirtualGroupThumbnailCard(
    group: VirtualVideoGroup,
    metadata: JvrMovieMetadata?,
    currentPreviewKey: String? = null,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }

    val posterUrl = metadata?.posterUrl
    val representativePath = group.representativeFile?.path
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }
    val previewMetadataLookupRequest = remember(representativePath) {
        representativePath?.let(::buildVideoMetadataLookupRequest)
    }

    var posterLoadFailed by remember(group.key, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster = !posterUrl.isNullOrBlank() && !posterLoadFailed
    val isPosterPopupEligible = remember(representativePath) {
        representativePath?.let(::shouldEnablePosterPopupForPath) == true
    }
    val previewSpec = remember(
        shouldUsePoster,
        posterUrl,
        posterCacheKey,
        representativePath,
        isPosterPopupEligible,
    ) {
        if (!isPosterPopupEligible) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = representativePath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        secondaryText,
        previewSpec,
        metadata,
        previewMetadataLookupRequest,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "group:${representativePath ?: group.key}",
            title = displayTitle,
            subtitle = secondaryText,
            previewSpec = previewSpec,
            metadata = metadata,
            metadataLookupRequest = previewMetadataLookupRequest,
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key

    LaunchedEffect(group.key, posterUrl, representativePath, posterLoadFailed) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            !representativePath.isNullOrBlank() -> "generated-video-frame"
            else -> "none"
        }

        val posterCached = if (!posterCacheKey.isNullOrBlank()) {
            val snapshot = imageLoader.diskCache?.openSnapshot(posterCacheKey)
            val exists = snapshot != null
            snapshot?.close()
            exists
        } else {
            false
        }

        Log.d(
            GROUP_THUMBNAIL_LOG_TAG,
            "Render group=${group.key} source=$source posterFailed=$posterLoadFailed posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
        )
    }

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardHovered by cardInteractionSource.collectIsHoveredAsState()
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    var popupAnchorBounds by remember(group.key) { mutableStateOf<IntRect?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource = cardInteractionSource)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isPreviewFocused) {
                        onPopupClick()
                    } else {
                        onPreviewFocused(previewItem)
                        onClick()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isPreviewFocused || isCardHovered || isCardFocused) {
            BorderStroke(1.dp, NetflixRed.copy(alpha = 0.7f))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(DividerGray)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        popupAnchorBounds = IntRect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt(),
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    shouldUsePoster -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(posterUrl)
                                .diskCacheKey(posterCacheKey)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load success for group=${group.key}, cacheKey=${posterCacheKey ?: "<none>"}"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.w(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Poster load failed for group=${group.key}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                        )
                                        posterLoadFailed = true
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(VideoThumbnailFetcher.Model(representativePath))
                                .diskCacheKey(VideoThumbnailFetcher.diskCacheKey(representativePath))
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .listener(
                                    onSuccess = { _, _ ->
                                        Log.d(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail success for group=${group.key}, path=$representativePath"
                                        )
                                    },
                                    onError = { _, result ->
                                        Log.e(
                                            GROUP_THUMBNAIL_LOG_TAG,
                                            "Generated thumbnail failed for group=${group.key}, path=$representativePath, reason=${result.throwable.message}"
                                        )
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Generated video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open grouped files",
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = secondaryText,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        GroupHoverPreviewPopup(
            show = false,
            groupKey = group.key,
            title = displayTitle,
            previewSpec = previewSpec,
            anchorBounds = popupAnchorBounds,
            onClick = onPopupClick,
        )
    }
}

@Composable
private fun FileListEntryCard(
    file: SMBFileItem,
    isVideoFile: Boolean,
    allowJvrMetadata: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    currentPreviewKey: String? = null,
    onFavoriteToggle: (() -> Unit)?,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val metadata = if (allowJvrMetadata) rememberVideoFileMetadata(file, isVideoFile) else null
    val displayTitle =
        if (allowJvrMetadata && isVideoFile) metadata?.title ?: file.name else file.name
    val subtitleText = if (allowJvrMetadata) metadata?.code ?: file.name else file.name
    val posterUrl = metadata?.posterUrl
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    var posterLoadFailed by remember(file.name, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster =
        allowJvrMetadata && isVideoFile && !posterUrl.isNullOrBlank() && !posterLoadFailed
    val generatedThumbnailModel = remember(file.path, allowJvrMetadata) {
        VideoThumbnailFetcher.Model(
            path = file.path,
            allowMetadataPoster = allowJvrMetadata,
        )
    }
    val generatedThumbnailDiskCacheKey = remember(file.path, allowJvrMetadata) {
        VideoThumbnailFetcher.diskCacheKey(
            path = file.path,
            allowMetadataPoster = allowJvrMetadata,
        )
    }
    val isPosterPopupEligible = remember(file.path) { shouldEnablePosterPopupForPath(file.path) }
    val previewMetadataLookupRequest = remember(file.path, allowJvrMetadata, isVideoFile) {
        if (allowJvrMetadata && isVideoFile) {
            buildVideoMetadataLookupRequest(file.path)
        } else {
            null
        }
    }

    val previewSpec = remember(
        file.path,
        isVideoFile,
        isPosterPopupEligible,
        shouldUsePoster,
        posterUrl,
        posterCacheKey,
    ) {
        if (!isVideoFile || !isPosterPopupEligible) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = file.path,
                allowMetadataPosterForGeneratedThumbnail = allowJvrMetadata,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        subtitleText,
        previewSpec,
        metadata,
        previewMetadataLookupRequest,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "file:${file.path}",
            title = displayTitle,
            subtitle = subtitleText,
            previewSpec = previewSpec,
            metadata = metadata,
            metadataLookupRequest = previewMetadataLookupRequest,
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key

    var popupAnchorBounds by remember(file.path) { mutableStateOf<IntRect?>(null) }

    FancyFileCard(
        fileName = displayTitle,
        isDirectory = file.isDirectory,
        isVideoFile = isVideoFile,
        fileSize = if (!file.isDirectory) {
            formatFileSizeHelper(file.size)
        } else {
            null
        },
        isFavorite = isFavorite,
        videoPath = if (isVideoFile) file.path else null,
        thumbnailModel = when {
            shouldUsePoster -> posterUrl
            isVideoFile -> generatedThumbnailModel
            else -> null
        },
        thumbnailDiskCacheKey = when {
            shouldUsePoster -> posterCacheKey
            isVideoFile -> generatedThumbnailDiskCacheKey
            else -> null
        },
        onThumbnailLoadSuccess = {
            if (shouldUsePoster) {
                Log.d(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Poster load success for file=${file.path}, cacheKey=${posterCacheKey ?: "<none>"}"
                )
            }
        },
        onThumbnailLoadError = { throwable ->
            if (shouldUsePoster) {
                Log.w(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Poster load failed for file=${file.path}, url=$posterUrl, reason=${throwable?.message}. Falling back to generated thumbnail."
                )
                posterLoadFailed = true
            } else if (isVideoFile) {
                Log.e(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Generated thumbnail failed for file=${file.path}, reason=${throwable?.message}"
                )
            }
        },
        onFavoriteToggle = if (isSelectionMode || !isVideoFile) {
            null
        } else {
            onFavoriteToggle
        },
        isHighlighted = isPreviewFocused,
        isSelected = isSelected,
        onClick = {
            if (isVideoFile && !isSelectionMode) {
                openPreviewSelectionIfFocused(
                    currentPreviewKey = currentPreviewKey,
                    previewItem = previewItem,
                    onPreviewFocused = onPreviewFocused,
                )
            } else {
                onClick()
            }
        },
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                popupAnchorBounds = IntRect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                )
            },
    )

    GroupHoverPreviewPopup(
        show = false,
        groupKey = subtitleText,
        title = displayTitle,
        previewSpec = previewSpec,
        anchorBounds = popupAnchorBounds,
        onClick = onPopupClick,
    )
}

@Composable
private fun FileThumbnailCard(
    file: SMBFileItem,
    isVideoFile: Boolean,
    allowJvrMetadata: Boolean,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    currentPreviewKey: String? = null,
    onFavoriteToggle: (() -> Unit)?,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metadata = if (allowJvrMetadata) rememberVideoFileMetadata(file, isVideoFile) else null
    val displayTitle =
        if (allowJvrMetadata && isVideoFile) metadata?.title ?: file.name else file.name
    val subtitleText = if (allowJvrMetadata) metadata?.code ?: file.name else file.name
    val posterUrl = metadata?.posterUrl
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    var posterLoadFailed by remember(file.name, posterUrl) { mutableStateOf(false) }
    val shouldUsePoster =
        allowJvrMetadata && isVideoFile && !posterUrl.isNullOrBlank() && !posterLoadFailed
    val generatedThumbnailModel = remember(file.path, allowJvrMetadata) {
        VideoThumbnailFetcher.Model(
            path = file.path,
            allowMetadataPoster = allowJvrMetadata,
        )
    }
    val generatedThumbnailDiskCacheKey = remember(file.path, allowJvrMetadata) {
        VideoThumbnailFetcher.diskCacheKey(
            path = file.path,
            allowMetadataPoster = allowJvrMetadata,
        )
    }
    val isPosterPopupEligible = remember(file.path) { shouldEnablePosterPopupForPath(file.path) }
    val previewMetadataLookupRequest = remember(file.path, allowJvrMetadata, isVideoFile) {
        if (allowJvrMetadata && isVideoFile) {
            buildVideoMetadataLookupRequest(file.path)
        } else {
            null
        }
    }

    val previewSpec = remember(
        file.path,
        isVideoFile,
        isPosterPopupEligible,
        shouldUsePoster,
        posterUrl,
        posterCacheKey,
    ) {
        if (!isVideoFile || !isPosterPopupEligible) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = file.path,
                allowMetadataPosterForGeneratedThumbnail = allowJvrMetadata,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        subtitleText,
        previewSpec,
        metadata,
        previewMetadataLookupRequest,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "file:${file.path}",
            title = displayTitle,
            subtitle = subtitleText,
            previewSpec = previewSpec,
            metadata = metadata,
            metadataLookupRequest = previewMetadataLookupRequest,
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardHovered by cardInteractionSource.collectIsHoveredAsState()
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    var popupAnchorBounds by remember(file.path) { mutableStateOf<IntRect?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource = cardInteractionSource)
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = {
                    if (isVideoFile && !isSelectionMode) {
                        openPreviewSelectionIfFocused(
                            currentPreviewKey = currentPreviewKey,
                            previewItem = previewItem,
                            onPreviewFocused = onPreviewFocused,
                        )
                    } else {
                        onClick()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected || isPreviewFocused || isCardHovered || isCardFocused) {
            BorderStroke(2.dp, if (isSelected) NetflixRed else NetflixRed.copy(alpha = 0.9f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(DividerGray)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        popupAnchorBounds = IntRect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt(),
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isVideoFile) {
                    val imageRequest = ImageRequest.Builder(context)
                        .data(
                            if (shouldUsePoster) {
                                posterUrl
                            } else {
                                generatedThumbnailModel
                            }
                        )
                        .diskCacheKey(
                            if (shouldUsePoster) {
                                posterCacheKey
                            } else {
                                generatedThumbnailDiskCacheKey
                            }
                        )
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onSuccess = { _, _ ->
                                if (shouldUsePoster) {
                                    Log.d(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Poster load success for file=${file.path}, cacheKey=${posterCacheKey ?: "<none>"}"
                                    )
                                }
                            },
                            onError = { _, result ->
                                if (shouldUsePoster) {
                                    Log.w(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Poster load failed for file=${file.path}, url=$posterUrl, reason=${result.throwable.message}. Falling back to generated thumbnail."
                                    )
                                    posterLoadFailed = true
                                } else {
                                    Log.e(
                                        FILE_THUMBNAIL_LOG_TAG,
                                        "Generated thumbnail failed for file=${file.path}, reason=${result.throwable.message}"
                                    )
                                }
                            }
                        )
                        .build()

                    AsyncImage(
                        model = imageRequest,
                        imageLoader = ThumbnailImageLoaderProvider.get(context),
                        contentDescription = if (shouldUsePoster) {
                            "Poster thumbnail"
                        } else {
                            "Video thumbnail"
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                if (!isSelectionMode && isVideoFile && onFavoriteToggle != null) {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                            contentDescription = if (isFavorite) {
                                "Remove from favorites"
                            } else {
                                "Add to favorites"
                            },
                            tint = if (isFavorite) NetflixRed else TextPrimary
                        )
                    }
                }

                if (isSelectionMode && isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected for deletion",
                        tint = NetflixRed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isVideoFile) TextPrimary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!file.isDirectory) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSizeHelper(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }

    GroupHoverPreviewPopup(
        show = false,
        groupKey = subtitleText,
        title = displayTitle,
        previewSpec = previewSpec,
        anchorBounds = popupAnchorBounds,
        onClick = onPopupClick,
    )
}

/**
 * RIGHT PANEL: Favorites + Recent
 */
private enum class LibraryTab {
    Favorites,
    Recent,
}

@Composable
private fun LibraryTabChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) NetflixRed.copy(alpha = 0.18f) else CardBackground,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) NetflixRed else DividerGray),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun FavoritesPanel(
    favorites: List<RecentVideo>,
    recentVideos: List<RecentVideo>,
    isConnected: Boolean,
    currentPreviewKey: String?,
    onFavoriteClick: (RecentVideo) -> Unit,
    onRecentClick: (RecentVideo) -> Unit,
    onFavoriteToggle: (RecentVideo) -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Favorites) }
    val recentItems = remember(recentVideos) {
        recentVideos.sortedByDescending { it.lastPlayed }
    }

    LaunchedEffect(isConnected, selectedTab, favorites, recentItems) {
        val shouldClearPreview = !isConnected ||
                (selectedTab == LibraryTab.Favorites && favorites.isEmpty()) ||
                (selectedTab == LibraryTab.Recent && recentItems.isEmpty())
        if (shouldClearPreview) {
            onPreviewFocused(null)
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = CardBackground,
        ),
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
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryTabChip(
                    text = "Favorites",
                    selected = selectedTab == LibraryTab.Favorites,
                    onClick = {
                        if (selectedTab != LibraryTab.Favorites) {
                            onPreviewFocused(null)
                        }
                        selectedTab = LibraryTab.Favorites
                    },
                )
                LibraryTabChip(
                    text = "Recent",
                    selected = selectedTab == LibraryTab.Recent,
                    onClick = {
                        if (selectedTab != LibraryTab.Recent) {
                            onPreviewFocused(null)
                        }
                        selectedTab = LibraryTab.Recent
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Filled.Cloud,
                        message = "Connect to view library",
                    )
                }

                selectedTab == LibraryTab.Favorites && favorites.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        message = "No favorites yet",
                    )
                }

                selectedTab == LibraryTab.Recent && recentItems.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Movie,
                        message = "No recent videos yet",
                    )
                }

                selectedTab == LibraryTab.Favorites -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(favorites) { video ->
                            val allowMetadataPoster = remember(video.fileName) {
                                shouldUseMetadataForIndividualVideo(video.fileName)
                            }
                            val displayTitle = remember(
                                video.fileName,
                                video.resolvedTitle,
                                allowMetadataPoster,
                            ) {
                                if (allowMetadataPoster) {
                                    video.resolvedTitle?.takeIf { it.isNotBlank() }
                                        ?: video.fileName
                                } else {
                                    video.fileName
                                }
                            }
                            val previewItem = remember(
                                video.id,
                                video.filePath,
                                video.fileName,
                                video.resolvedTitle,
                                video.thumbnailPath,
                                allowMetadataPoster,
                            ) {
                                val previewMetadataLookupRequest =
                                    buildVideoMetadataLookupRequest(video.filePath)
                                DashboardPreviewItem(
                                    key = "library:${video.filePath}",
                                    title = displayTitle,
                                    subtitle = video.fileName,
                                    previewSpec = buildLibraryPreviewSpec(
                                        video = video,
                                        allowMetadataPosterForGeneratedThumbnail = allowMetadataPoster,
                                    ),
                                    metadataLookupRequest = previewMetadataLookupRequest,
                                    onOpen = { onFavoriteClick(video) },
                                )
                            }

                            FancyMovieCard(
                                video = video,
                                isFavorite = true,
                                displayTitleOverride = displayTitle,
                                allowMetadataPoster = allowMetadataPoster,
                                onClick = { onFavoriteClick(video) },
                                onFavoriteToggle = { onFavoriteToggle(video) },
                                isPreviewFocused = currentPreviewKey == previewItem.key,
                                onRequestPreview = { onPreviewFocused(previewItem) },
                                onHoverFocusChanged = { isFocused ->
                                    if (isFocused) onPreviewFocused(previewItem)
                                },
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recentItems) { video ->
                            val isFavorite = video.isFavorite || favorites.any { it.id == video.id }
                            val allowMetadataPoster = remember(video.fileName) {
                                shouldUseMetadataForIndividualVideo(video.fileName)
                            }
                            val displayTitle = remember(
                                video.fileName,
                                video.resolvedTitle,
                                allowMetadataPoster,
                            ) {
                                if (allowMetadataPoster) {
                                    video.resolvedTitle?.takeIf { it.isNotBlank() }
                                        ?: video.fileName
                                } else {
                                    video.fileName
                                }
                            }
                            val previewItem = remember(
                                video.id,
                                video.filePath,
                                video.fileName,
                                video.resolvedTitle,
                                video.thumbnailPath,
                                allowMetadataPoster,
                            ) {
                                val previewMetadataLookupRequest =
                                    buildVideoMetadataLookupRequest(video.filePath)
                                DashboardPreviewItem(
                                    key = "library:${video.filePath}",
                                    title = displayTitle,
                                    subtitle = video.fileName,
                                    previewSpec = buildLibraryPreviewSpec(
                                        video = video,
                                        allowMetadataPosterForGeneratedThumbnail = allowMetadataPoster,
                                    ),
                                    metadataLookupRequest = previewMetadataLookupRequest,
                                    onOpen = { onRecentClick(video) },
                                )
                            }

                            FancyMovieCard(
                                video = video,
                                isFavorite = isFavorite,
                                displayTitleOverride = displayTitle,
                                allowMetadataPoster = allowMetadataPoster,
                                onClick = { onRecentClick(video) },
                                onFavoriteToggle = {
                                    onFavoriteToggle(video.copy(isFavorite = isFavorite))
                                },
                                isPreviewFocused = currentPreviewKey == previewItem.key,
                                onRequestPreview = { onPreviewFocused(previewItem) },
                                onHoverFocusChanged = { isFocused ->
                                    if (isFocused) onPreviewFocused(previewItem)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * Add Server Dialog
 */
/**
 * Add/Edit Server Dialog
 */
@Composable
private fun AddServerDialog(
    initialServer: SavedServer? = null,
    defaultUsername: String = "",
    defaultPassword: String = "",
    onDismiss: () -> Unit,
    onSave: (SavedServer) -> Unit,
    onTestConnection: suspend (SavedServer) -> Result<Unit>
) {
    val context = LocalContext.current
    val (storedUsername, storedPassword) = remember {
        ServerCredentialAutofillStore.load(context)
    }

    val buildConfigUsername = remember { BuildConfig.SMB_DEFAULT_USERNAME.trim() }
    val buildConfigPassword = remember { BuildConfig.SMB_DEFAULT_PASSWORD.trim() }

    val initialUsername = remember(
        initialServer?.id,
        defaultUsername,
        storedUsername,
        buildConfigUsername,
    ) {
        if (initialServer != null) {
            initialServer.username
        } else {
            listOf(storedUsername, defaultUsername, buildConfigUsername)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
    }

    val initialPassword = remember(
        initialServer?.id,
        defaultPassword,
        storedPassword,
        buildConfigPassword,
    ) {
        if (initialServer != null) {
            initialServer.password
        } else {
            listOf(storedPassword, defaultPassword, buildConfigPassword)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
    }

    var name by remember(initialServer?.id) { mutableStateOf(initialServer?.serverName ?: "") }
    var address by remember(initialServer?.id) {
        mutableStateOf(
            initialServer?.serverAddress ?: ""
        )
    }
    var shareName by remember(initialServer?.id) { mutableStateOf(initialServer?.shareName ?: "") }
    var username by remember(initialServer?.id, initialUsername) { mutableStateOf(initialUsername) }
    var password by remember(initialServer?.id, initialPassword) { mutableStateOf(initialPassword) }
    var domain by remember(initialServer?.id) { mutableStateOf(initialServer?.domain ?: "") }

    LaunchedEffect(initialServer?.id, initialUsername, initialPassword) {
        Log.d(
            "AddServerDialog",
            "Autofill ready (editing=${initialServer != null}, usernameLength=${initialUsername.length}, hasPassword=${initialPassword.isNotBlank()})"
        )
    }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Use standard Dialog composable
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = CardBackground
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (initialServer == null) "Add Server" else "Edit Server",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Server Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("e.g., 192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = shareName,
                        onValueChange = { shareName = it },
                        label = { Text("Share Name (Optional)") },
                        placeholder = { Text("e.g., Videos") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text("Domain (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                }

                if (connectionError != null) {
                    Text(
                        text = connectionError!!,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isTestingConnection
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val isSaveEnabled =
                        name.isNotBlank() && address.isNotBlank() && !isTestingConnection

                    Button(
                        onClick = {
                            if (isSaveEnabled) {
                                scope.launch {
                                    isTestingConnection = true
                                    connectionError = null

                                    val serverToSave = SavedServer(
                                        id = initialServer?.id ?: 0, // Preserve ID if editing
                                        serverName = name,
                                        serverAddress = address,
                                        shareName = shareName,
                                        username = username,
                                        password = password,
                                        domain = domain
                                    )

                                    val result = onTestConnection(serverToSave)

                                    if (result.isSuccess) {
                                        ServerCredentialAutofillStore.save(
                                            context,
                                            username,
                                            password
                                        )
                                        Log.d(
                                            "AddServerDialog",
                                            "Stored manual autofill credentials (usernameLength=${username.length}, hasPassword=${password.isNotBlank()})"
                                        )
                                        onSave(serverToSave)
                                    } else {
                                        connectionError =
                                            "Connection failed: ${result.exceptionOrNull()?.message}"
                                        isTestingConnection = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetflixRed
                        ),
                        enabled = isSaveEnabled
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

private fun buildGroupPosterCacheKey(groupKey: String, posterUrl: String): String {
    return "group-poster:$groupKey:${posterUrl.hashCode()}"
}

private fun buildFilePosterCacheKey(fileName: String, posterUrl: String): String {
    return "file-poster:${fileName.hashCode()}:${posterUrl.hashCode()}"
}

private val multipartVideoPattern = Regex("""^(.+)-(\d{1,2})$""")

private fun buildVirtualVideoGroups(files: List<SMBFileItem>): Map<String, VirtualVideoGroup> {
    val groupedCandidates = files
        .asSequence()
        .filter { !it.isDirectory && SMBClient.isVideoFile(it.name) }
        .mapNotNull { file ->
            val groupKey = extractVirtualGroupKey(file.name) ?: return@mapNotNull null
            groupKey to file
        }
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
        .filterValues { candidates -> candidates.size > 1 }

    return groupedCandidates.mapValues { (groupKey, groupedFiles) ->
        VirtualVideoGroup(
            key = groupKey,
            files = groupedFiles.sortedWith(
                compareBy<SMBFileItem>(
                    { extractVirtualGroupPart(it.name) ?: Int.MAX_VALUE },
                    { it.name.lowercase() }
                )
            )
        )
    }
}

private fun buildFileBrowserDisplayItems(
    files: List<SMBFileItem>,
    virtualGroups: Map<String, VirtualVideoGroup>,
    activeGroupKey: String?
): List<FileBrowserDisplayItem> {
    if (activeGroupKey != null) {
        val activeGroup = virtualGroups[activeGroupKey] ?: return emptyList()
        return activeGroup.files.map { groupedFile ->
            FileBrowserDisplayItem.Entry(
                file = groupedFile,
                isVirtualGroupMember = true,
            )
        }
    }

    val emittedGroupKeys = mutableSetOf<String>()
    val displayItems = mutableListOf<FileBrowserDisplayItem>()

    files.forEach { file ->
        if (file.isDirectory) {
            displayItems += FileBrowserDisplayItem.Entry(file = file)
            return@forEach
        }

        if (!SMBClient.isVideoFile(file.name)) {
            return@forEach
        }

        val groupKey = extractVirtualGroupKey(file.name)
        val virtualGroup = groupKey?.let(virtualGroups::get)

        if (virtualGroup == null) {
            displayItems += FileBrowserDisplayItem.Entry(file = file)
            return@forEach
        }

        if (!emittedGroupKeys.add(virtualGroup.key)) {
            return@forEach
        }

        displayItems += FileBrowserDisplayItem.Group(
            virtualGroup = virtualGroup,
        )
    }

    return displayItems
}

private fun extractVirtualGroupKey(fileName: String): String? {
    val stem = fileName.substringBeforeLast('.', fileName)
    val match = multipartVideoPattern.matchEntire(stem) ?: return null
    val baseName = match.groupValues[1].trimEnd('-', '_', ' ')
    return baseName.takeIf { it.isNotBlank() }
}

private fun extractVirtualGroupPart(fileName: String): Int? {
    val stem = fileName.substringBeforeLast('.', fileName)
    val match = multipartVideoPattern.matchEntire(stem) ?: return null
    return match.groupValues[2].toIntOrNull()
}

/**
 * Helper function to format file size
 */
private fun formatFileSizeHelper(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
