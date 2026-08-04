@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package blackark.app.vr.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.South
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.xr.compose.spatial.ContentEdge
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterOffsetType
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.layout.MovePolicy
import androidx.xr.compose.subspace.layout.ResizePolicy
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.rotate
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.unit.DpVolumeSize
import androidx.xr.runtime.math.Pose
import blackark.app.vr.AppState
import blackark.app.vr.BuildConfig
import blackark.app.vr.R
import blackark.app.vr.data.database.entity.FavoriteVideo
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.data.database.entity.MetadataScope
import blackark.app.vr.data.database.entity.QuickAccessFolder
import blackark.app.vr.data.security.SmbCredentialStore
import blackark.app.vr.data.security.SmbCredentials
import blackark.app.vr.data.model.LibraryVideoItem
import blackark.app.vr.network.SMBClient
import blackark.app.vr.network.SMBFileItem
import blackark.app.vr.network.SmbEndpointParser
import blackark.app.vr.player.SMBDataSource
import blackark.app.vr.ui.ApplyHandTrackingPreference
import blackark.app.vr.ui.components.EmptyState
import blackark.app.vr.ui.components.FancyFileCard
import blackark.app.vr.ui.components.FancyMovieCard
import blackark.app.vr.ui.components.FancyServerCard
import blackark.app.vr.ui.navigation.Screen
import blackark.app.vr.ui.theme.AccentGold
import blackark.app.vr.ui.theme.CardBackground
import blackark.app.vr.ui.theme.CardBackgroundHover
import blackark.app.vr.ui.theme.DividerGray
import blackark.app.vr.ui.theme.ErrorRed
import blackark.app.vr.ui.theme.NetflixRed
import blackark.app.vr.ui.theme.SuccessGreen
import blackark.app.vr.ui.theme.TextPrimary
import blackark.app.vr.ui.theme.TextSecondary
import blackark.app.vr.ui.theme.TextTertiary
import blackark.app.vr.ui.viewmodel.AvFilterFamily
import blackark.app.vr.ui.viewmodel.AvLibraryState
import blackark.app.vr.ui.viewmodel.AvVrFilterOption
import blackark.app.vr.ui.viewmodel.FileBrowserSortMode
import blackark.app.vr.ui.viewmodel.FileBrowserViewMode
import blackark.app.vr.ui.viewmodel.MainDashboardViewModel
import blackark.app.vr.ui.viewmodel.SubtitleTextSize
import blackark.app.vr.utils.AppSettingsStore
import blackark.app.vr.utils.BrowserFolderArtworkResolution
import blackark.app.vr.utils.IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS
import blackark.app.vr.utils.IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS
import blackark.app.vr.utils.IMMERSIVE_UI_HORIZONTAL_OFFSET_SLIDER_STEPS
import blackark.app.vr.utils.ImageCacheVersionStore
import blackark.app.vr.utils.JvrCastMetadata
import blackark.app.vr.utils.JvrLibraryMetadataProvider
import blackark.app.vr.utils.JvrMovieMetadata
import blackark.app.vr.utils.SubtitleFontCatalog
import blackark.app.vr.utils.SubtitleFontOption
import blackark.app.vr.utils.MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS
import blackark.app.vr.utils.MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS
import blackark.app.vr.utils.MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS
import blackark.app.vr.utils.MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS
import blackark.app.vr.utils.ThumbnailImageLoaderProvider
import blackark.app.vr.utils.VideoThumbnailFetcher
import blackark.app.vr.utils.extractNormalizedCodeFromFileName
import blackark.app.vr.utils.extractVirtualGroupKey
import blackark.app.vr.utils.groupMultipartVideoFiles
import blackark.app.vr.utils.BrowserFolderArtworkKind
import blackark.app.vr.utils.extractVirtualGroupPart
import blackark.app.vr.utils.parseVideoIdentity
import blackark.app.vr.utils.resolveParentFolderBaseName
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt
import androidx.xr.compose.subspace.layout.onGloballyPositioned as onSubspaceGloballyPositioned

/**
 * Main Dashboard Screen - Material 3 dashboard with XR pane layout support.
 */
private enum class DashboardPaneDestination(val label: String) {
    Servers("Servers"),
    Browser("Browse"),
    Library("Library"),
}

private enum class PrimaryDestination {
    Home,
    LocalFiles,
    SmbFiles,
    Library,
    YouTube,
    Settings,
}

private fun logMetadataTrace(tag: String, message: String) {
    Log.v(tag, message)
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

private enum class FileWorkspaceMode {
    Browser,
    Av,
}

private data class DashboardContentState(
    val destination: PrimaryDestination,
    val workspaceMode: FileWorkspaceMode,
    val isLocalConnected: Boolean,
    val isSmbConnected: Boolean,
    val selectedSource: SavedServer?,
)

private enum class SettingsAction {
    ClearArtwork,
    ClearThumbnails,
    ClearRecentHistory,
    ClearFavorites,
}

private data class SettingsFeedback(
    val action: SettingsAction,
    val detail: String? = null,
)

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
    val fallbackPreviewSpec: GroupHoverPreviewSpec? = null,
    val metadata: JvrMovieMetadata? = null,
    val metadataLookupRequest: VideoMetadataLookupRequest? = null,
    val onPreviewLoadError: ((Throwable?) -> Unit)? = null,
    val onOpen: (() -> Unit)? = null,
)

private fun DashboardPaneDestination.icon(): ImageVector = when (this) {
    DashboardPaneDestination.Servers -> Icons.Filled.Cloud
    DashboardPaneDestination.Browser -> Icons.Filled.FolderOpen
    DashboardPaneDestination.Library -> Icons.Filled.Favorite
}

private fun PrimaryDestination.icon(): ImageVector = when (this) {
    PrimaryDestination.Home -> Icons.Filled.Home
    PrimaryDestination.LocalFiles -> Icons.Filled.FolderOpen
    PrimaryDestination.SmbFiles -> Icons.Filled.Cloud
    PrimaryDestination.Library -> Icons.Filled.Favorite
    PrimaryDestination.YouTube -> Icons.Filled.Movie
    PrimaryDestination.Settings -> Icons.Filled.Settings
}

@Composable
private fun PrimaryDestination.localizedLabel(): String = when (this) {
    PrimaryDestination.Home -> stringResource(R.string.home)
    PrimaryDestination.LocalFiles -> stringResource(R.string.local_files)
    PrimaryDestination.SmbFiles -> stringResource(R.string.smb_files)
    PrimaryDestination.Library -> stringResource(R.string.library)
    PrimaryDestination.YouTube -> stringResource(R.string.youtube)
    PrimaryDestination.Settings -> stringResource(R.string.settings)
}

private fun resolveLocalFolderDisplayName(treeUri: String?): String? {
    if (treeUri.isNullOrBlank()) return null

    val tree = Uri.parse(treeUri)

    return try {
        DocumentsContract.getTreeDocumentId(tree)
            .substringAfter(':', tree.lastPathSegment.orEmpty())
            .takeIf { it.isNotBlank() }
            ?: tree.lastPathSegment
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        tree.lastPathSegment
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
    }
}

private fun buildLocalPickerInitialUri(pathSegment: String): Uri? {
    return try {
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$pathSegment",
        )
    } catch (_: Throwable) {
        null
    }
}

private fun resolvePreferredLocalPickerInitialUri(configuredTreeUri: String?): Uri? {
    configuredTreeUri?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
    return buildLocalPickerInitialUri("Download")
        ?: buildLocalPickerInitialUri("Documents")
}

private fun resolveLocalBreadcrumbLabel(
    currentPath: String,
    rootLabel: String?,
): String? {
    if (currentPath.isBlank()) {
        return rootLabel
    }

    return try {
        DocumentsContract.getDocumentId(Uri.parse(currentPath))
            .substringAfter(':', currentPath)
            .takeIf { it.isNotBlank() }
            ?: rootLabel
    } catch (_: Throwable) {
        rootLabel
    }
}

private fun resolveSourceTitle(
    destination: PrimaryDestination,
    selectedServer: SavedServer?,
): String = when (destination) {
    PrimaryDestination.Home -> "Home"
    PrimaryDestination.LocalFiles -> "Local Files"
    PrimaryDestination.SmbFiles -> selectedServer?.serverName?.takeIf { it.isNotBlank() }
        ?: "SMB Files"

    PrimaryDestination.Library -> "Library"
    PrimaryDestination.YouTube -> "YouTube"
    PrimaryDestination.Settings -> "Settings"
}

private fun resolveSourceBreadcrumb(
    destination: PrimaryDestination,
    selectedServer: SavedServer?,
    currentPath: String,
    localRootLabel: String?,
): String? = when (destination) {
    PrimaryDestination.Home -> null
    PrimaryDestination.LocalFiles -> resolveLocalBreadcrumbLabel(
        currentPath = currentPath,
        rootLabel = localRootLabel,
    )

    PrimaryDestination.SmbFiles -> when {
        currentPath.isNotBlank() -> currentPath
        selectedServer?.shareName?.isNotBlank() == true -> selectedServer.shareName
        selectedServer?.serverAddress?.isNotBlank() == true -> selectedServer.serverAddress
        else -> null
    }

    PrimaryDestination.Library,
    PrimaryDestination.YouTube,
    PrimaryDestination.Settings -> null
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
    destinations: List<DashboardPaneDestination> = DashboardPaneDestination.entries,
    showLayoutToggle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = CardBackground.copy(alpha = 0.92f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        NavigationRail(
            containerColor = Color.Transparent,
        ) {
            Surface(
                color = NetflixRed.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp),
            ) {
                IconButton(onClick = onAddServerClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_server),
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
                        contentDescription = stringResource(R.string.toggle_pane_layout),
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
}

@Composable
private fun DashboardSourceRail(
    selectedMode: PrimaryDestination,
    onModeSelected: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(124.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        NavigationRail(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 14.dp),
            containerColor = Color.Transparent,
        ) {
            PrimaryDestination.entries.forEach { mode ->
                NavigationRailItem(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    icon = {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = mode.localizedLabel(),
                        )
                    },
                    label = {
                        Text(
                            text = mode.localizedLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = selectedMode == mode,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = TextPrimary,
                        selectedTextColor = TextPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextTertiary,
                        indicatorColor = NetflixRed.copy(alpha = 0.24f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DashboardSourceBar(
    selectedMode: PrimaryDestination,
    onModeSelected: (PrimaryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(720.dp)
            .clip(RoundedCornerShape(28.dp)),
        color = CardBackground.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            PrimaryDestination.entries.forEach { mode ->
                NavigationBarItem(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    icon = {
                        Icon(
                            imageVector = mode.icon(),
                            contentDescription = mode.localizedLabel(),
                        )
                    },
                    label = {
                        Text(
                            text = mode.localizedLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = selectedMode == mode,
                )
            }
        }
    }
}

@Composable
private fun DashboardHeaderChip(
    text: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (selected) {
            NetflixRed.copy(alpha = 0.16f)
        } else {
            CardBackgroundHover.copy(alpha = 0.96f)
        },
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 4.dp else 2.dp,
        border = BorderStroke(
            1.dp,
            if (selected) NetflixRed.copy(alpha = 0.36f) else DividerGray.copy(alpha = 0.78f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) NetflixRed else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) TextPrimary else TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CompactSourceSwitcherButton(
    selectedSource: SavedServer?,
    availableSources: List<SavedServer>,
    onSourceSelected: (SavedServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectedSource?.serverName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.smb_files)

    Box(modifier = modifier) {
        DashboardHeaderChip(
            text = label,
            icon = Icons.Filled.Cloud,
            onClick = { expanded = true },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = CardBackgroundHover.copy(alpha = 0.98f),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.85f)),
        ) {
            availableSources.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = server.serverName,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${server.serverAddress}:${server.port}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = NetflixRed.copy(alpha = 0.92f),
                        )
                    },
                    trailingIcon = {
                        if (selectedSource?.id == server.id) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSourceSelected(server)
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalSourceHeader(
    folderName: String?,
    onChangeFolder: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CardBackgroundHover.copy(alpha = 0.95f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 10.dp,
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                color = CardBackground.copy(alpha = 0.9f),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.7f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGold.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = AccentGold,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.local_files),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextTertiary,
                        )
                        Text(
                            text = folderName ?: stringResource(R.string.local_folder_ready),
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            TextButton(onClick = onChangeFolder) {
                Text(stringResource(R.string.change_folder), color = AccentGold)
            }

            TextButton(onClick = onDisconnect) {
                Text(stringResource(R.string.disconnect), color = NetflixRed)
            }
        }
    }
}

@Composable
private fun LocalSourceLandingPanel(
    folderName: String?,
    isConnecting: Boolean,
    onOpenLocalFiles: () -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackgroundHover),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, DividerGray.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccentGold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.local_files),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = folderName ?: stringResource(R.string.no_local_folder_selected),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (folderName == null) {
                    stringResource(R.string.choose_local_folder_message)
                } else {
                    stringResource(R.string.saved_local_folder_message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onOpenLocalFiles,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NetflixRed,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(
                        text = if (folderName == null) {
                            stringResource(R.string.choose_folder)
                        } else {
                            stringResource(R.string.open_local_files)
                        }
                    )
                }

                Button(
                    onClick = onChangeFolder,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardBackground,
                        contentColor = TextPrimary,
                    ),
                ) {
                    Text(stringResource(R.string.change_folder))
                }
            }
        }
    }
}

private const val YOUTUBE_HOME_URL = "https://m.youtube.com/"
private const val HOME_VIDEO_NAVIGATION_INPUT_SETTLE_MS = 80L

@Composable
private fun HomePanel(
    recentVideos: List<RecentVideo>,
    quickAccessFolders: List<QuickAccessFolder>,
    favorites: List<FavoriteVideo>,
    servers: List<SavedServer>,
    selectedSource: SavedServer?,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    onContinueClick: (RecentVideo) -> Unit,
    onContinueRemove: (RecentVideo) -> Unit,
    onQuickAccessClick: (QuickAccessFolder) -> Unit,
    onQuickAccessDelete: (QuickAccessFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedRecentVideos = remember(recentVideos, servers) {
        recentVideos
            .filter { it.hasAvailableHomeSource(servers) }
            .sortedByDescending { it.lastPlayed }
            .distinctBy { it.filePath }
    }
    val continueWatching = remember(sortedRecentVideos) {
        sortedRecentVideos.filter { video ->
            video.lastPosition > 0L &&
                    (video.duration <= 0L || video.lastPosition < video.duration * 95L / 100L)
        }
    }
    val availableFavorites = remember(favorites, servers) {
        favorites.filter { it.hasAvailableHomeSource(servers) }.distinctBy { it.filePath }
    }
    val favoritePaths = remember(availableFavorites) {
        availableFavorites.mapTo(hashSetOf()) { it.filePath }
    }
    val serverNamesById = remember(servers) { servers.associate { it.id to it.serverName } }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            HomeVideoShelf(
                title = stringResource(R.string.continue_watching),
                videos = continueWatching,
                viewModel = viewModel,
                metadataRefreshToken = metadataRefreshToken,
                selectedSource = selectedSource,
                favoritePaths = favoritePaths,
                showProgress = true,
                artworkPolicy = VideoArtworkPolicy.GeneratedFrameOnly,
                showScrollControls = true,
                onRemove = onContinueRemove,
                onVideoClick = onContinueClick,
            )
        }

        if (quickAccessFolders.isNotEmpty()) {
            item {
                HomeQuickAccessShelf(
                    folders = quickAccessFolders,
                    serverNamesById = serverNamesById,
                    onFolderClick = onQuickAccessClick,
                    onFolderDelete = onQuickAccessDelete,
                )
            }
        }

    }
}

private fun LibraryVideoItem.hasAvailableHomeSource(servers: List<SavedServer>): Boolean {
    if (!filePath.startsWith("smb://", ignoreCase = true)) return true
    return servers.any { source -> matchesHomeVideoSource(source) }
}

private fun LibraryVideoItem.matchesHomeVideoSource(source: SavedServer): Boolean {
    val isSmbVideo = filePath.startsWith("smb://", ignoreCase = true)
    if (!isSmbVideo) return source.isLocalStorage
    if (source.isLocalStorage) return false

    val parsedIdentity = parseVideoIdentity(filePath)
    val effectiveServerAddress = serverAddress.takeIf(String::isNotBlank)
        ?: parsedIdentity?.serverAddress.orEmpty()
    if (!source.serverAddress.equals(effectiveServerAddress, ignoreCase = true)) return false

    // An empty configured share represents the server root and can reach every share.
    if (source.shareName.isBlank()) return true
    val effectiveShareName = shareName.takeIf(String::isNotBlank)
        ?: parsedIdentity?.shareName.orEmpty()
    return source.shareName.equals(effectiveShareName, ignoreCase = true)
}

@Composable
private fun <T : LibraryVideoItem> HomeVideoShelf(
    title: String,
    videos: List<T>,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    selectedSource: SavedServer?,
    favoritePaths: Set<String>,
    showProgress: Boolean,
    artworkPolicy: VideoArtworkPolicy = VideoArtworkPolicy.MetadataPreferred,
    showScrollControls: Boolean = false,
    onRemove: ((T) -> Unit)? = null,
    onVideoClick: (T) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showScrollControls) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSectionHeader(
                    title = title,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        enabled = listState.canScrollBackward,
                        onClick = {
                            val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                                .coerceAtLeast(1)
                            val targetIndex = (listState.firstVisibleItemIndex - visibleCount)
                                .coerceAtLeast(0)
                            scope.launch { listState.animateScrollToItem(targetIndex) }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.scroll_previous_items),
                        )
                    }
                    IconButton(
                        enabled = listState.canScrollForward,
                        onClick = {
                            val visibleCount = listState.layoutInfo.visibleItemsInfo.size
                                .coerceAtLeast(1)
                            val targetIndex = (listState.firstVisibleItemIndex + visibleCount)
                                .coerceAtMost(videos.lastIndex)
                            scope.launch { listState.animateScrollToItem(targetIndex) }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.scroll_next_items),
                        )
                    }
                }
            }
        } else {
            HomeSectionHeader(title = title)
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(
                start = 8.dp,
                top = 6.dp,
                end = 8.dp,
                bottom = 6.dp,
            ),
        ) {
            items(videos, key = { it.filePath }) { video ->
                HomeVideoCard(
                    video = video,
                    isFavorite = video.filePath in favoritePaths,
                    showProgress = showProgress,
                    viewModel = viewModel,
                    metadataRefreshToken = metadataRefreshToken,
                    selectedSource = selectedSource,
                    artworkPolicy = artworkPolicy,
                    onRemove = onRemove?.let { remove -> { remove(video) } },
                    onClick = { onVideoClick(video) },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
    }
}

@Composable
private fun HomeVideoCard(
    video: LibraryVideoItem,
    isFavorite: Boolean,
    showProgress: Boolean,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    selectedSource: SavedServer?,
    artworkPolicy: VideoArtworkPolicy,
    onRemove: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(video.filePath, video.fileName) {
        SMBFileItem(
            name = video.fileName,
            path = video.filePath,
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
        )
    }
    val sourceMatches = selectedSource?.let { source ->
        video.matchesHomeVideoSource(source)
    } == true
    val metadataState = if (
        artworkPolicy == VideoArtworkPolicy.MetadataPreferred && sourceMatches
    ) {
        rememberVideoFileMetadata(
            viewModel = viewModel,
            file = file,
            isVideoFile = true,
            refreshToken = metadataRefreshToken,
        )
    } else {
        ArtworkState.Missing
    }
    val metadata = metadataState.resolvedValueOrNull()
    var posterFailureCount by remember(
        video.filePath,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = selectVideoArtwork(
        policy = artworkPolicy,
        metadataState = metadataState,
        posterFailureCount = posterFailureCount,
        videoPath = video.filePath,
    )
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val resumeFrameTimeMs = video.lastPosition.takeIf { showProgress && it > 0L }
    val validGeneratedFramePath = if (artworkSelection is VideoArtworkSelection.GeneratedFrame) {
        VideoThumbnailFetcher.currentGeneratedFramePath(
            context = context,
            videoPath = video.filePath,
            thumbnailPath = video.thumbnailPath,
            frameTimeMs = resumeFrameTimeMs,
            durationMs = video.duration,
        )
    } else {
        null
    }
    val thumbnailModel = when (artworkSelection) {
        is VideoArtworkSelection.Poster -> artworkSelection.url
        is VideoArtworkSelection.GeneratedFrame -> {
            validGeneratedFramePath ?: if (sourceMatches) {
                VideoThumbnailFetcher.Model(
                    path = artworkSelection.videoPath,
                    frameTimeMs = resumeFrameTimeMs,
                    durationMs = video.duration,
                )
            } else {
                null
            }
        }

        VideoArtworkSelection.Placeholder -> null
    }
    val thumbnailDiskCacheKey = when (artworkSelection) {
        is VideoArtworkSelection.Poster -> buildFilePosterCacheKey(video.fileName, artworkSelection.url)
        is VideoArtworkSelection.GeneratedFrame -> {
            validGeneratedFramePath ?: VideoThumbnailFetcher.diskCacheKey(
                path = artworkSelection.videoPath,
                frameTimeMs = resumeFrameTimeMs,
                durationMs = video.duration,
            )
        }

        VideoArtworkSelection.Placeholder -> null
    }
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() }
        ?: video.resolvedTitle?.takeIf { it.isNotBlank() }
        ?: video.fileName.substringBeforeLast('.', video.fileName)
    val sourceLabel = metadata?.studio?.takeIf { it.isNotBlank() }
        ?: if (video.filePath.startsWith("smb://", ignoreCase = true)) {
            video.shareName.takeIf { it.isNotBlank() } ?: video.serverAddress
        } else {
            stringResource(R.string.local_storage)
        }
    val progress = if (showProgress && video.duration > 0L && video.lastPosition > 0L) {
        (video.lastPosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = isHovered || isFocused || isPressed
    val cardScale by animateFloatAsState(
        targetValue = if (isActive) 1.025f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "home-video-card-scale",
    )

    Card(
        modifier = Modifier
            .width(276.dp)
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CardBackgroundHover else CardBackground,
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
            } else {
                DividerGray.copy(alpha = 0.64f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(42.dp),
                )

                if (thumbnailModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbnailModel)
                            .diskCacheKey(thumbnailDiskCacheKey)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .listener(
                                onError = { _, _ ->
                                    if (posterUrl != null) posterFailureCount += 1
                                },
                            )
                            .build(),
                        imageLoader = ThumbnailImageLoaderProvider.get(context),
                        contentDescription = stringResource(R.string.video_thumbnail),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = if (artworkSelection is VideoArtworkSelection.Poster) {
                            ContentScale.Fit
                        } else {
                            ContentScale.Crop
                        },
                    )
                }

                if (isFavorite) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.favorites),
                            tint = NetflixRed,
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }

                onRemove?.let { remove ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        IconButton(
                            onClick = remove,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.remove_from_recent),
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                progress?.let { watchedProgress ->
                    LinearProgressIndicator(
                        progress = { watchedProgress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HomeQuickAccessShelf(
    folders: List<QuickAccessFolder>,
    serverNamesById: Map<Long, String>,
    onFolderClick: (QuickAccessFolder) -> Unit,
    onFolderDelete: (QuickAccessFolder) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.quick_access),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(folders, key = { it.id }) { folder ->
                HomeQuickAccessCard(
                    folder = folder,
                    serverName = serverNamesById[folder.serverId]?.takeIf { it.isNotBlank() },
                    onClick = { onFolderClick(folder) },
                    onDelete = { onFolderDelete(folder) },
                )
            }
        }
    }
}

@Composable
private fun HomeQuickAccessCard(
    folder: QuickAccessFolder,
    serverName: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isActive = isHovered || isFocused
    val cardScale by animateFloatAsState(
        targetValue = if (isActive) 1.025f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "home-folder-card-scale",
    )

    Surface(
        modifier = Modifier
            .width(252.dp)
            .scale(cardScale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = if (isActive) CardBackgroundHover else CardBackground,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) AccentGold.copy(alpha = 0.8f) else DividerGray.copy(alpha = 0.68f),
        ),
        tonalElevation = if (isActive) 6.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AccentGold.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = serverName ?: stringResource(R.string.smb_files),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = TextTertiary,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePanel(
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by rememberSaveable { mutableStateOf(YOUTUBE_HOME_URL) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun syncNavigationState(view: WebView?) {
        currentUrl = view?.url ?: currentUrl
        canGoBack = view?.canGoBack() == true
        canGoForward = view?.canGoForward() == true
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.youtube),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            webView?.goBack()
                            syncNavigationState(webView)
                        },
                        enabled = canGoBack,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = if (canGoBack) TextPrimary else TextTertiary,
                        )
                    }

                    IconButton(
                        onClick = {
                            webView?.goForward()
                            syncNavigationState(webView)
                        },
                        enabled = canGoForward,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.youtube_forward),
                            tint = if (canGoForward) TextPrimary else TextTertiary,
                        )
                    }

                    IconButton(
                        onClick = {
                            webView?.reload()
                            syncNavigationState(webView)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.youtube_reload),
                            tint = TextPrimary,
                        )
                    }

                    IconButton(
                        onClick = {
                            currentUrl = YOUTUBE_HOME_URL
                            webView?.loadUrl(YOUTUBE_HOME_URL)
                            syncNavigationState(webView)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = stringResource(R.string.youtube_home),
                            tint = NetflixRed,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(12.dp))

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                syncNavigationState(view)
                            }
                        }
                        loadUrl(currentUrl)
                        webView = this
                        syncNavigationState(this)
                    }
                },
                update = { view ->
                    webView = view
                    syncNavigationState(view)
                    if (view.url.isNullOrBlank()) {
                        view.loadUrl(currentUrl)
                    }
                },
            )
        }
    }
}

@Composable
private fun DashboardPreviewPanel(
    previewItem: DashboardPreviewItem?,
    viewModel: MainDashboardViewModel,
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
                var primaryPreviewLoadFailed by remember(
                    previewItem.key,
                    previewItem.previewSpec,
                ) { mutableStateOf(false) }
                val previewSpec = if (primaryPreviewLoadFailed) {
                    previewItem.fallbackPreviewSpec ?: previewItem.previewSpec
                } else {
                    previewItem.previewSpec
                }
                if (previewSpec != null) {
                    val motionPreviewPath =
                        previewSpec.trailerPath ?: previewSpec.fallbackVideoPath
                    val motionPreviewEnabled = remember(context, motionPreviewPath) {
                        isMotionPreviewEnabled(context, motionPreviewPath)
                    }
                    val shouldResolveExtraFanart =
                        motionPreviewEnabled &&
                            previewSpec.trailerPath == null &&
                            !previewSpec.extraFanartLookupPath.isNullOrBlank()
                    val extraFanartPaths by produceState<List<String>?>(
                        initialValue = if (shouldResolveExtraFanart) null else emptyList(),
                        previewItem.key,
                        previewSpec.trailerPath,
                        previewSpec.extraFanartLookupPath,
                        shouldResolveExtraFanart,
                    ) {
                        value = if (shouldResolveExtraFanart) {
                            viewModel.resolveExtraFanartPaths(previewSpec.extraFanartLookupPath)
                        } else {
                            emptyList()
                        }
                    }
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
                            onState = { state ->
                                if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                    if (
                                        previewSpec.source == "poster" &&
                                        previewItem.fallbackPreviewSpec != null
                                    ) {
                                        primaryPreviewLoadFailed = true
                                    }
                                    previewItem.onPreviewLoadError?.invoke(state.result.throwable)
                                }
                            },
                        )
                        when {
                            motionPreviewEnabled && previewSpec.trailerPath != null -> {
                                MotionVideoPreview(
                                    videoPath = previewSpec.trailerPath,
                                    source = "trailer",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            !extraFanartPaths.isNullOrEmpty() -> {
                                ExtraFanartSlideshow(
                                    imagePaths = extraFanartPaths.orEmpty(),
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            motionPreviewEnabled &&
                                extraFanartPaths != null &&
                                previewSpec.fallbackVideoPath != null -> {
                                MotionVideoPreview(
                                    videoPath = previewSpec.fallbackVideoPath,
                                    source = "main-fallback",
                                    playbackLimitMs = MAIN_VIDEO_PREVIEW_DURATION_MS,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
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

private const val MAIN_VIDEO_PREVIEW_DURATION_MS = 60_000L

@Composable
private fun MotionVideoPreview(
    videoPath: String,
    source: String,
    playbackLimitMs: Long? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    if (videoPath.isBlank()) return
    val isSmb = videoPath.startsWith("smb://", ignoreCase = true)
    val enabled = remember(videoPath) {
        isMotionPreviewEnabled(appContext, videoPath)
    }
    if (!enabled) return

    var previewPlayer by remember(videoPath, source) { mutableStateOf<ExoPlayer?>(null) }
    var hasRenderedFirstFrame by remember(videoPath, source) { mutableStateOf(false) }
    LaunchedEffect(videoPath, source, playbackLimitMs) {
        delay(800L)
        val readySignal = CompletableDeferred<Boolean>()
        val firstFrameSignal = CompletableDeferred<Unit>()
        val endedSignal = CompletableDeferred<Unit>()
        val player = runCatching {
            val builder = ExoPlayer.Builder(appContext)
            if (isSmb) {
                val config = AppState.smbConfig ?: return@runCatching null
                builder.setMediaSourceFactory(
                    DefaultMediaSourceFactory(SMBDataSource.Factory(config))
                )
            } else {
                builder.setMediaSourceFactory(
                    DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext))
                )
            }
            builder.build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(videoPath))
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && !readySignal.isCompleted) {
                            readySignal.complete(true)
                        }
                        if (playbackState == Player.STATE_ENDED && !endedSignal.isCompleted) {
                            endedSignal.complete(Unit)
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(
                            "MotionVideoPreview",
                            "Preview player failed source=$source path=$videoPath reason=${error.message}",
                            error,
                        )
                        if (!readySignal.isCompleted) readySignal.complete(false)
                        if (!endedSignal.isCompleted) endedSignal.complete(Unit)
                    }

                    override fun onRenderedFirstFrame() {
                        hasRenderedFirstFrame = true
                        if (!firstFrameSignal.isCompleted) firstFrameSignal.complete(Unit)
                    }
                })
                prepare()
            }
        }.onFailure { error ->
            Log.e(
                "MotionVideoPreview",
                "Could not create preview player source=$source path=$videoPath reason=${error.message}",
                error,
            )
        }.getOrNull() ?: return@LaunchedEffect

        try {
            // Attach an invisible surface while buffering so the video decoder can reach READY.
            // The poster remains visible until onRenderedFirstFrame confirms real output.
            previewPlayer = player
            Log.d(
                "MotionVideoPreview",
                "Preparing source=$source path=$videoPath limitMs=$playbackLimitMs",
            )
            val isReady = withTimeoutOrNull(20_000L) { readySignal.await() } == true
            if (!isReady) {
                Log.w(
                    "MotionVideoPreview",
                    "Preview prepare timed out source=$source path=$videoPath",
                )
                return@LaunchedEffect
            }

            player.seekTo(0L)
            player.play()

            val renderedFirstFrame = withTimeoutOrNull(10_000L) {
                firstFrameSignal.await()
                true
            } == true
            if (!renderedFirstFrame) {
                Log.w(
                    "MotionVideoPreview",
                    "Preview first frame timed out source=$source path=$videoPath",
                )
                return@LaunchedEffect
            }

            Log.d(
                "MotionVideoPreview",
                "Playing source=$source path=$videoPath startMs=0 limitMs=$playbackLimitMs",
            )
            if (playbackLimitMs == null) {
                endedSignal.await()
            } else {
                val endedNaturally = withTimeoutOrNull(playbackLimitMs) {
                    endedSignal.await()
                    true
                } == true
                if (!endedNaturally) {
                    player.pause()
                    Log.d(
                        "MotionVideoPreview",
                        "Playback limit reached source=$source path=$videoPath limitMs=$playbackLimitMs",
                    )
                }
            }
        } finally {
            if (previewPlayer === player) previewPlayer = null
            hasRenderedFirstFrame = false
            player.release()
            Log.d("MotionVideoPreview", "Released source=$source path=$videoPath")
        }
    }

    previewPlayer?.let { player ->
        AndroidView(
            modifier = modifier
                .alpha(if (hasRenderedFirstFrame) 1f else 0f)
                .background(Color.Black),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view -> view.player = player },
        )
    }
}

private fun isMotionPreviewEnabled(context: Context, path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val appContext = context.applicationContext
    val isSmb = path.startsWith("smb://", ignoreCase = true)
    return AppSettingsStore.isLibraryHoverPreviewEnabled(appContext) &&
        (!isSmb || AppSettingsStore.isSmbHoverPreviewEnabled(appContext))
}

@Composable
private fun ExtraFanartSlideshow(
    imagePaths: List<String>,
    modifier: Modifier = Modifier,
) {
    if (imagePaths.isEmpty()) return
    val context = LocalContext.current
    var currentIndex by remember(imagePaths) { mutableStateOf(0) }

    LaunchedEffect(imagePaths) {
        currentIndex = 0
        if (imagePaths.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4_000L)
            currentIndex = (currentIndex + 1) % imagePaths.size
        }
    }

    AnimatedContent(
        targetState = imagePaths[currentIndex.coerceIn(imagePaths.indices)],
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) togetherWith
                fadeOut(animationSpec = tween(600))
        },
        label = "extra-fanart-slideshow",
        modifier = modifier,
    ) { imagePath ->
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imagePath)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            imageLoader = ThumbnailImageLoaderProvider.get(context),
            contentDescription = "Extra fanart preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
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
        key3 = lookupRequest?.folderPath to previewItem?.metadata,
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

private data class AppVersionInfo(
    val packageVersionName: String,
    val versionCode: String,
    val playStoreVersion: String,
    val internalVersion: String,
    val releaseChannel: String,
    val gitSha: String,
    val buildTimeUtc: String,
)

private fun resolveAppVersionInfo(context: Context): AppVersionInfo {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
    }.getOrNull()

    val packageVersionName = packageInfo?.versionName?.takeIf { it.isNotBlank() }
        ?: BuildConfig.INTERNAL_VERSION_NAME.ifBlank { "Unknown" }
    val versionCode = packageInfo?.longVersionCode?.toString()
        ?: BuildConfig.INTERNAL_VERSION_CODE.takeIf { it > 0 }?.toString()
        ?: "Unknown"
    val playStoreVersion = BuildConfig.PLAY_STORE_VERSION.ifBlank {
        packageVersionName.substringBefore('-')
    }
    val internalVersion = BuildConfig.INTERNAL_DISPLAY_VERSION.ifBlank {
        val channel = BuildConfig.INTERNAL_RELEASE_CHANNEL.ifBlank { "internal" }
        val minor = BuildConfig.INTERNAL_MINOR_VERSION.takeIf { it >= 0 } ?: 0
        "$channel.$minor"
    }

    return AppVersionInfo(
        packageVersionName = packageVersionName,
        versionCode = versionCode,
        playStoreVersion = playStoreVersion,
        internalVersion = internalVersion,
        releaseChannel = BuildConfig.INTERNAL_RELEASE_CHANNEL.ifBlank { "unknown" },
        gitSha = BuildConfig.INTERNAL_GIT_SHA.ifBlank { "unknown" },
        buildTimeUtc = BuildConfig.INTERNAL_BUILD_TIME_UTC.ifBlank { "unknown" },
    )
}

private fun deleteTrackedThumbnailFiles(directory: File) {
    if (!directory.exists()) return

    if (!directory.deleteRecursively()) {
        throw IllegalStateException("Failed to delete ${directory.absolutePath}")
    }
}

private suspend fun resetArtworkState(context: Context) =
    withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val thumbnailImageLoader = ThumbnailImageLoaderProvider.get(appContext)

        thumbnailImageLoader.memoryCache?.clear()
        deleteTrackedThumbnailFiles(File(appContext.filesDir, "group_posters"))
        ImageCacheVersionStore.bumpArtworkGeneration(appContext)

        val database = blackark.app.vr.data.database.AppDatabase.getDatabase(appContext)
        database.virtualGroupMetadataDao().clearAll()
        JvrLibraryMetadataProvider.clearMemoryCaches()
    }

private suspend fun resetThumbnailState(context: Context) =
    withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val thumbnailImageLoader = ThumbnailImageLoaderProvider.get(appContext)

        thumbnailImageLoader.memoryCache?.clear()

        deleteTrackedThumbnailFiles(File(appContext.filesDir, "thumbnails"))
        ImageCacheVersionStore.bumpThumbnailGeneration(appContext)

        val database = blackark.app.vr.data.database.AppDatabase.getDatabase(appContext)
        database.videoDao().clearAllThumbnailPaths()
        database.favoriteVideoDao().clearAllThumbnailPaths()
    }

@Composable
private fun SourceSwitcherButton(
    selectedSource: SavedServer?,
    availableSources: List<SavedServer>,
    onSourceSelected: (SavedServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sourceLabel = selectedSource?.serverName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.source)

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { expanded = true },
            color = CardBackground.copy(alpha = 0.9f),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NetflixRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = NetflixRed,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.source),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                    )
                    Text(
                        text = sourceLabel,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = CardBackgroundHover.copy(alpha = 0.98f),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.85f)),
        ) {
            availableSources.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = server.serverName,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${server.serverAddress}:${server.port}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = NetflixRed.copy(alpha = 0.92f),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSourceSelected(server)
                    },
                    trailingIcon = {
                        if (selectedSource?.id == server.id) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    versionInfo: AppVersionInfo,
    servers: List<SavedServer>,
    selectedServer: SavedServer?,
    metadataScopes: List<MetadataScope>,
    isHandTrackingEnabled: Boolean,
    isAvBackgroundIndexingEnabled: Boolean,
    activeAction: SettingsAction?,
    onHandTrackingChange: (Boolean) -> Unit,
    onAvBackgroundIndexingChange: (Boolean) -> Unit,
    onAddCurrentMetadataFolder: () -> Unit,
    onMetadataScopeEnabled: (MetadataScope, Boolean) -> Unit,
    onMetadataScopeRecursive: (MetadataScope, Boolean) -> Unit,
    onDeleteMetadataScope: (MetadataScope) -> Unit,
    onRescanMetadata: () -> Unit,
    onClearArtwork: () -> Unit,
    onClearThumbnails: () -> Unit,
    onClearRecentHistory: () -> Unit,
    onClearFavorites: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val subtitleFonts = remember { SubtitleFontCatalog.availableFonts() }
    var selectedSubtitleFontId by remember {
        mutableStateOf(
            SubtitleFontCatalog.resolveOption(
                fontId = AppSettingsStore.getSubtitleFontId(context),
                availableFonts = subtitleFonts,
            ).id
        )
    }
    var selectedSubtitleTextSize by remember {
        mutableStateOf(
            AppSettingsStore.getSubtitleTextSize(context)
                ?.let { stored -> SubtitleTextSize.entries.firstOrNull { it.name == stored } }
                ?: SubtitleTextSize.Medium
        )
    }
    var selectedImmersiveSubtitleDistanceMeters by remember {
        mutableStateOf(AppSettingsStore.getImmersiveSubtitleDistanceMeters(context))
    }
    var selectedImmersiveSubtitleVerticalOffsetMeters by remember {
        mutableStateOf(AppSettingsStore.getImmersiveSubtitleVerticalOffsetMeters(context))
    }
    var selectedImmersiveUiHorizontalOffsetMeters by remember {
        mutableStateOf(AppSettingsStore.getImmersiveUiHorizontalOffsetMeters(context))
    }
    var hoverPreviewEnabled by remember {
        mutableStateOf(AppSettingsStore.isLibraryHoverPreviewEnabled(context))
    }
    var smbHoverPreviewEnabled by remember {
        mutableStateOf(AppSettingsStore.isSmbHoverPreviewEnabled(context))
    }
    var playbackUiHeadFollowEnabled by remember {
        mutableStateOf(AppSettingsStore.isPlaybackUiHeadFollowEnabled(context))
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )

            HorizontalDivider(color = DividerGray)

            SettingsToggleRow(
                title = stringResource(R.string.hand_tracking),
                description = stringResource(R.string.hand_tracking_description),
                checked = isHandTrackingEnabled,
                onCheckedChange = onHandTrackingChange,
            )
            SettingsToggleRow(
                title = stringResource(R.string.playback_ui_head_follow),
                description = stringResource(R.string.playback_ui_head_follow_description),
                checked = playbackUiHeadFollowEnabled,
                onCheckedChange = { enabled ->
                    playbackUiHeadFollowEnabled = enabled
                    AppSettingsStore.setPlaybackUiHeadFollowEnabled(context, enabled)
                },
            )
            SettingsToggleRow(
                title = stringResource(R.string.av_background_indexing),
                description = stringResource(R.string.av_background_indexing_description),
                checked = isAvBackgroundIndexingEnabled,
                onCheckedChange = onAvBackgroundIndexingChange,
            )
            SettingsToggleRow(
                title = stringResource(R.string.hover_preview),
                description = stringResource(R.string.hover_preview_description),
                checked = hoverPreviewEnabled,
                onCheckedChange = { enabled ->
                    hoverPreviewEnabled = enabled
                    AppSettingsStore.setLibraryHoverPreviewEnabled(context, enabled)
                },
            )
            SettingsToggleRow(
                title = stringResource(R.string.smb_hover_preview),
                description = stringResource(R.string.smb_hover_preview_description),
                checked = smbHoverPreviewEnabled,
                onCheckedChange = { enabled ->
                    smbHoverPreviewEnabled = enabled
                    AppSettingsStore.setSmbHoverPreviewEnabled(context, enabled)
                },
            )

            MetadataScopeSettingsSection(
                servers = servers.filterNot(SavedServer::isLocalStorage),
                selectedServer = selectedServer?.takeUnless(SavedServer::isLocalStorage),
                scopes = metadataScopes,
                onAddCurrentFolder = onAddCurrentMetadataFolder,
                onEnabledChange = onMetadataScopeEnabled,
                onRecursiveChange = onMetadataScopeRecursive,
                onDelete = onDeleteMetadataScope,
                onRescan = onRescanMetadata,
            )

            HorizontalDivider(color = DividerGray)

            Text(
                text = stringResource(R.string.subtitle_settings),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            SettingsSubtitleFontPicker(
                fonts = subtitleFonts,
                selectedFontId = selectedSubtitleFontId,
                onFontSelected = { option ->
                    selectedSubtitleFontId = option.id
                    AppSettingsStore.setSubtitleFontId(context, option.id)
                },
            )
            SettingsSubtitleSizePicker(
                selectedSize = selectedSubtitleTextSize,
                onSizeSelected = { size ->
                    selectedSubtitleTextSize = size
                    AppSettingsStore.setSubtitleTextSize(context, size.name)
                },
            )
            SettingsImmersiveSubtitleDistancePicker(
                distanceMeters = selectedImmersiveSubtitleDistanceMeters,
                onDistanceChange = { distanceMeters ->
                    selectedImmersiveSubtitleDistanceMeters = distanceMeters
                },
                onDistanceChangeFinished = {
                    AppSettingsStore.setImmersiveSubtitleDistanceMeters(
                        context,
                        selectedImmersiveSubtitleDistanceMeters,
                    )
                },
            )
            SettingsImmersiveSubtitleVerticalOffsetPicker(
                offsetMeters = selectedImmersiveSubtitleVerticalOffsetMeters,
                onOffsetChange = { offsetMeters ->
                    selectedImmersiveSubtitleVerticalOffsetMeters = offsetMeters
                },
                onOffsetChangeFinished = {
                    AppSettingsStore.setImmersiveSubtitleVerticalOffsetMeters(
                        context,
                        selectedImmersiveSubtitleVerticalOffsetMeters,
                    )
                },
            )
            SettingsImmersiveUiHorizontalOffsetPicker(
                offsetMeters = selectedImmersiveUiHorizontalOffsetMeters,
                onOffsetChange = { offsetMeters ->
                    selectedImmersiveUiHorizontalOffsetMeters = offsetMeters
                },
                onOffsetChangeFinished = {
                    AppSettingsStore.setImmersiveUiHorizontalOffsetMeters(
                        context,
                        selectedImmersiveUiHorizontalOffsetMeters,
                    )
                },
            )

            HorizontalDivider(color = DividerGray)

            SettingsActionRow(
                title = stringResource(R.string.clear_artwork),
                description = stringResource(R.string.clear_artwork_description),
                buttonLabel = if (activeAction == SettingsAction.ClearArtwork) {
                    stringResource(R.string.settings_action_clearing)
                } else {
                    stringResource(R.string.settings_action_clear)
                },
                enabled = activeAction == null,
                onClick = onClearArtwork,
            )
            SettingsActionRow(
                title = stringResource(R.string.clear_thumbnails),
                description = stringResource(R.string.clear_thumbnails_description),
                buttonLabel = if (activeAction == SettingsAction.ClearThumbnails) {
                    stringResource(R.string.settings_action_clearing)
                } else {
                    stringResource(R.string.settings_action_clear)
                },
                enabled = activeAction == null,
                onClick = onClearThumbnails,
            )
            SettingsActionRow(
                title = stringResource(R.string.clear_recent_history),
                description = stringResource(R.string.clear_recent_history_description),
                buttonLabel = if (activeAction == SettingsAction.ClearRecentHistory) {
                    stringResource(R.string.settings_action_clearing)
                } else {
                    stringResource(R.string.settings_action_clear)
                },
                enabled = activeAction == null,
                onClick = onClearRecentHistory,
            )
            SettingsActionRow(
                title = stringResource(R.string.clear_favorites),
                description = stringResource(R.string.clear_favorites_description),
                buttonLabel = if (activeAction == SettingsAction.ClearFavorites) {
                    stringResource(R.string.settings_action_clearing)
                } else {
                    stringResource(R.string.settings_action_clear)
                },
                enabled = activeAction == null,
                onClick = onClearFavorites,
            )
            SettingsValueRow(
                title = stringResource(R.string.version),
                value = versionInfo.packageVersionName,
            )
            SettingsValueRow(
                title = stringResource(R.string.play_store_version),
                value = versionInfo.playStoreVersion,
            )
            SettingsValueRow(
                title = stringResource(R.string.internal_version),
                value = versionInfo.internalVersion,
            )
            SettingsValueRow(
                title = stringResource(R.string.version_code),
                value = versionInfo.versionCode,
            )
            SettingsValueRow(
                title = stringResource(R.string.release_channel),
                value = versionInfo.releaseChannel,
            )
            SettingsValueRow(
                title = stringResource(R.string.git_commit),
                value = versionInfo.gitSha,
            )
            SettingsValueRow(
                title = stringResource(R.string.build_time_utc),
                value = versionInfo.buildTimeUtc,
            )
        }
    }
}

@Composable
private fun MetadataScopeSettingsSection(
    servers: List<SavedServer>,
    selectedServer: SavedServer?,
    scopes: List<MetadataScope>,
    onAddCurrentFolder: () -> Unit,
    onEnabledChange: (MetadataScope, Boolean) -> Unit,
    onRecursiveChange: (MetadataScope, Boolean) -> Unit,
    onDelete: (MetadataScope) -> Unit,
    onRescan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.metadata_folders),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.metadata_folders_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAddCurrentFolder,
                enabled = selectedServer?.id?.let { it > 0L } == true,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
            ) {
                Text(stringResource(R.string.add_current_folder))
            }
            Button(
                onClick = onRescan,
                enabled = selectedServer != null,
            ) {
                Text(stringResource(R.string.rescan_metadata))
            }
        }

        servers.forEach { server ->
            val serverScopes = scopes.filter { it.serverId == server.id }
            Text(
                text = server.serverName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            if (serverScopes.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_metadata_folders),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
            serverScopes.forEach { scope ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = scope.displayPath,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = TextPrimary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.enabled), color = TextSecondary)
                                Switch(
                                    checked = scope.enabled,
                                    onCheckedChange = { onEnabledChange(scope, it) },
                                )
                            }
                            TextButton(
                                onClick = {
                                    onRecursiveChange(scope, !scope.includeDescendants)
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (scope.includeDescendants) {
                                            R.string.include_subfolders
                                        } else {
                                            R.string.current_folder_only
                                        }
                                    )
                                )
                            }
                            IconButton(onClick = { onDelete(scope) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
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
private fun SettingsSubtitleFontPicker(
    fonts: List<SubtitleFontOption>,
    selectedFontId: String,
    onFontSelected: (SubtitleFontOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFont = remember(fonts, selectedFontId) {
        SubtitleFontCatalog.resolveOption(selectedFontId, fonts)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.subtitle_font),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.subtitle_font_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardBackgroundHover,
                    contentColor = TextPrimary,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SubtitleFontPreviewText(
                    option = selectedFont,
                    text = selectedFont.displayName,
                    textColor = TextPrimary,
                    textSizeSp = 16f,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 520.dp)
                    .heightIn(max = 420.dp),
            ) {
                fonts.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            SubtitleFontPreviewText(
                                option = option,
                                text = option.displayName,
                                textColor = TextPrimary,
                                textSizeSp = 16f,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp),
                            )
                        },
                        trailingIcon = {
                            if (option.id == selectedFont.id) {
                                Text(
                                    text = "✓",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        },
                        onClick = {
                            onFontSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackgroundHover),
            shape = RoundedCornerShape(12.dp),
        ) {
            SubtitleFontPreviewText(
                option = selectedFont,
                text = stringResource(R.string.subtitle_font_preview),
                textColor = TextPrimary,
                textSizeSp = 22f,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 44.dp),
            )
        }
    }
}

@Composable
private fun SettingsSubtitleSizePicker(
    selectedSize: SubtitleTextSize,
    onSizeSelected: (SubtitleTextSize) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.subtitle_size),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            text = stringResource(R.string.subtitle_size_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleTextSize.entries.forEach { size ->
                val selected = size == selectedSize
                Button(
                    onClick = { onSizeSelected(size) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) NetflixRed else CardBackgroundHover,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(text = "${(size.scale * 100).roundToInt()}%")
                }
            }
        }
    }
}

@Composable
private fun SettingsImmersiveSubtitleDistancePicker(
    distanceMeters: Float,
    onDistanceChange: (Float) -> Unit,
    onDistanceChangeFinished: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.immersive_subtitle_distance),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_distance_value,
                    distanceMeters,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        Text(
            text = stringResource(R.string.immersive_subtitle_distance_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Slider(
            value = distanceMeters,
            onValueChange = onDistanceChange,
            onValueChangeFinished = onDistanceChangeFinished,
            valueRange =
                MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS..
                        MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
            steps = IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = NetflixRed,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = CardBackgroundHover,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_distance_value,
                    MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_distance_value,
                    MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SettingsImmersiveSubtitleVerticalOffsetPicker(
    offsetMeters: Float,
    onOffsetChange: (Float) -> Unit,
    onOffsetChangeFinished: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.immersive_subtitle_vertical_offset),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_vertical_offset_value,
                    offsetMeters * 100f,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        Text(
            text = stringResource(R.string.immersive_subtitle_vertical_offset_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Slider(
            value = offsetMeters,
            onValueChange = onOffsetChange,
            onValueChangeFinished = onOffsetChangeFinished,
            valueRange =
                MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS..
                        MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
            steps = IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = NetflixRed,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = CardBackgroundHover,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_vertical_offset_value,
                    MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS * 100f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_subtitle_vertical_offset_value,
                    MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS * 100f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SettingsImmersiveUiHorizontalOffsetPicker(
    offsetMeters: Float,
    onOffsetChange: (Float) -> Unit,
    onOffsetChangeFinished: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.immersive_ui_horizontal_offset),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_ui_horizontal_offset_value,
                    offsetMeters * 100f,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
            )
        }
        Text(
            text = stringResource(R.string.immersive_ui_horizontal_offset_description),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
        Slider(
            value = offsetMeters,
            onValueChange = onOffsetChange,
            onValueChangeFinished = onOffsetChangeFinished,
            valueRange =
                MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS..
                        MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
            steps = IMMERSIVE_UI_HORIZONTAL_OFFSET_SLIDER_STEPS,
            colors = SliderDefaults.colors(
                thumbColor = NetflixRed,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = CardBackgroundHover,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.immersive_ui_horizontal_offset_value,
                    MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS * 100f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Text(
                text = stringResource(
                    R.string.immersive_ui_horizontal_offset_value,
                    MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS * 100f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SubtitleFontPreviewText(
    option: SubtitleFontOption,
    text: String,
    textColor: Color,
    textSizeSp: Float,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val typeface = remember(option.id) { SubtitleFontCatalog.resolveTypeface(option) }
    val colorArgb = textColor.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
            }
        },
        update = { view ->
            view.text = text
            view.typeface = typeface
            view.setTextColor(colorArgb)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            view.isSingleLine = singleLine
            view.maxLines = if (singleLine) 1 else 2
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
            ) {
                Text(
                    text = buttonLabel,
                    color = TextPrimary,
                )
            }
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    navController: NavController,
    viewModel: MainDashboardViewModel,
    hasControllerLikeInputDevice: Boolean,
    hasHandTrackingPermission: Boolean,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val metadataScopes by viewModel.metadataScopes.collectAsStateWithLifecycle()
    val quickAccessFolders by viewModel.quickAccessFolders.collectAsStateWithLifecycle()
    val allRecentVideos by viewModel.allRecentVideos.collectAsStateWithLifecycle()
    val allFavorites by viewModel.allFavorites.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoritePaths = remember(favorites) { favorites.mapTo(linkedSetOf()) { it.filePath } }

    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<SavedServer?>(null) }
    var primaryDestination by rememberSaveable {
        mutableStateOf(PrimaryDestination.Home)
    }
    var fileWorkspaceMode by rememberSaveable {
        mutableStateOf(FileWorkspaceMode.Browser)
    }
    val browserPreviewResetKey =
        "preview:${primaryDestination.name}:${uiState.selectedServer?.id}:${uiState.currentPath}"
    val libraryPreviewResetKey =
        "library-preview:${primaryDestination.name}:${uiState.selectedServer?.id}"
    var browserPreviewItem by remember(browserPreviewResetKey) {
        mutableStateOf<DashboardPreviewItem?>(
            null
        )
    }
    var libraryPreviewItem by remember(libraryPreviewResetKey) {
        mutableStateOf<DashboardPreviewItem?>(
            null
        )
    }
    var connectedPreviewWidthOverrideDp by rememberSaveable { mutableStateOf<Float?>(null) }
    var pendingSettingsAction by remember { mutableStateOf<SettingsAction?>(null) }
    var activeSettingsAction by remember { mutableStateOf<SettingsAction?>(null) }
    var settingsFeedback by remember { mutableStateOf<SettingsFeedback?>(null) }
    var pendingHomeVideo by remember { mutableStateOf<LibraryVideoItem?>(null) }
    val dashboardScope = rememberCoroutineScope()
    val appVersionInfo = remember(context) { resolveAppVersionInfo(context) }
    val shouldShowControllerHandTrackingPrompt =
        hasControllerLikeInputDevice &&
                uiState.isHandTrackingEnabled &&
                !uiState.isControllerHandTrackingPromptHandled
    fun clearAllPreviewItems() {
        browserPreviewItem = null
        libraryPreviewItem = null
    }

    val localStoragePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { treeUri ->
            if (treeUri == null) {
                return@rememberLauncherForActivityResult
            }

            val grantFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, grantFlags)
            }

            clearAllPreviewItems()
            fileWorkspaceMode = FileWorkspaceMode.Browser
            viewModel.connectToLocalTree(treeUri)
        }
    )

    val availableSmbSources = remember(servers) {
        servers
            .filterNot { it.isLocalStorage }
            .sortedByDescending { it.lastConnected }
    }
    val persistedLocalTreeUri =
        uiState.selectedServer
            ?.takeIf { it.isLocalStorage }
            ?.shareName
            ?: AppSettingsStore.getLocalStorageTreeUri(context.applicationContext)
    val localFolderName = remember(persistedLocalTreeUri) {
        resolveLocalFolderDisplayName(persistedLocalTreeUri)
    }
    val isLocalModeConnected =
        uiState.isConnected && uiState.selectedServer?.isLocalStorage == true
    val isSmbModeConnected =
        uiState.isConnected && uiState.selectedServer?.isLocalStorage == false
    val density = LocalDensity.current
    val initialDashboardPanelSize = remember { AppState.dashboardPanelSize.value }
    var dashboardPanelWidth by remember {
        mutableStateOf(initialDashboardPanelSize.widthDp.dp)
    }
    var dashboardPanelHeight by remember {
        mutableStateOf(initialDashboardPanelSize.heightDp.dp)
    }
    val useIntegratedSourceRail =
        resolveDashboardWidthClass(dashboardPanelWidth) == DashboardWidthClass.Expanded
    val initialDashboardPose = remember { AppState.dashboardPanelPose.value?.let { Pose(it) } }

    fun requestLocalStorageConnection(forcePicker: Boolean = false) {
        val configuredTreeUri =
            AppSettingsStore.getLocalStorageTreeUri(context.applicationContext)
        if (forcePicker || configuredTreeUri.isNullOrBlank()) {
            localStoragePickerLauncher.launch(
                resolvePreferredLocalPickerInitialUri(
                    configuredTreeUri = if (forcePicker) configuredTreeUri else null,
                )
            )
            return
        }

        viewModel.connectToServer(
            SavedServer.createLocalStorageServer().copy(shareName = configuredTreeUri)
        )
    }

    fun connectToSource(server: SavedServer) {
        clearAllPreviewItems()
        fileWorkspaceMode = FileWorkspaceMode.Browser

        if (server.isLocalStorage) {
            val shouldForcePicker = uiState.selectedServer?.isLocalStorage == true
            requestLocalStorageConnection(forcePicker = shouldForcePicker)
            return
        }

        viewModel.connectToServer(server)
    }

    fun openHomeVideo(video: LibraryVideoItem) {
        pendingHomeVideo = video
        clearAllPreviewItems()
        val isSmbVideo = video.filePath.startsWith("smb://", ignoreCase = true)
        if (isSmbVideo) {
            val server = availableSmbSources.firstOrNull { source ->
                video.matchesHomeVideoSource(source)
            } ?: run {
                pendingHomeVideo = null
                return
            }
            if (!isSmbModeConnected || uiState.selectedServer?.id != server.id) {
                fileWorkspaceMode = FileWorkspaceMode.Browser
                primaryDestination = PrimaryDestination.SmbFiles
                viewModel.connectToServer(server)
            }
        } else if (!isLocalModeConnected) {
            fileWorkspaceMode = FileWorkspaceMode.Browser
            primaryDestination = PrimaryDestination.LocalFiles
            requestLocalStorageConnection()
        }
    }

    LaunchedEffect(pendingHomeVideo, uiState.isConnected, uiState.selectedServer?.id) {
        val video = pendingHomeVideo ?: return@LaunchedEffect
        if (!uiState.isConnected) return@LaunchedEffect
        val sourceMatches = uiState.selectedServer?.let { source ->
            video.matchesHomeVideoSource(source)
        } == true
        if (sourceMatches) {
            // Let the SpatialMainPanel finish dispatching the click before navigation
            // removes its SceneCore parent.
            delay(HOME_VIDEO_NAVIGATION_INPUT_SETTLE_MS)
            pendingHomeVideo = null
            navController.navigate(Screen.VideoPlayer.createRoute(video.filePath, video.fileName)) {
                launchSingleTop = true
            }
        }
    }

    fun disconnectActiveSource() {
        clearAllPreviewItems()
        fileWorkspaceMode = FileWorkspaceMode.Browser
        viewModel.disconnect()
    }

    fun selectPrimaryDestination(destination: PrimaryDestination) {
        if (primaryDestination == destination) return

        primaryDestination = destination
        clearAllPreviewItems()
        if (destination == PrimaryDestination.LocalFiles || destination == PrimaryDestination.SmbFiles) {
            fileWorkspaceMode = FileWorkspaceMode.Browser
        }
    }

    LaunchedEffect(
        primaryDestination,
        persistedLocalTreeUri,
        uiState.isConnected,
        uiState.selectedServer?.id
    ) {
        when (primaryDestination) {
            PrimaryDestination.Home -> Unit
            PrimaryDestination.LocalFiles -> {
                viewModel.switchToLocalSource(persistedLocalTreeUri)
            }

            PrimaryDestination.SmbFiles -> {
                viewModel.switchToSmbSource()
            }

            PrimaryDestination.Library,
            PrimaryDestination.YouTube,
            PrimaryDestination.Settings -> Unit
        }
    }

    fun launchSettingsAction(action: SettingsAction) {
        if (activeSettingsAction != null) return

        activeSettingsAction = action
        settingsFeedback = null

        dashboardScope.launch {
            runCatching {
                when (action) {
                    SettingsAction.ClearArtwork -> {
                        resetArtworkState(context)
                        viewModel.clearAvMetadataLinks()
                        clearAllPreviewItems()
                    }

                    SettingsAction.ClearThumbnails -> {
                        resetThumbnailState(context)
                        clearAllPreviewItems()
                    }

                    SettingsAction.ClearRecentHistory -> {
                        viewModel.clearRecentHistory()
                    }

                    SettingsAction.ClearFavorites -> {
                        viewModel.clearAllFavorites()
                    }
                }
            }.onSuccess {
                settingsFeedback = SettingsFeedback(action = action)
            }
                .onFailure { throwable ->
                    settingsFeedback = SettingsFeedback(
                        action = action,
                        detail = throwable.message ?: "Unknown error",
                    )
                }

            activeSettingsAction = null
        }
    }

    Subspace {
        ApplyHandTrackingPreference(
            isHandTrackingEnabled = uiState.isHandTrackingEnabled,
            hasHandTrackingPermission = hasHandTrackingPermission,
            logTag = "MainDashboardScreen",
        )

        val dashboardPanelModifier =
            initialDashboardPose?.let { pose ->
                SubspaceModifier
                    .width(dashboardPanelWidth)
                    .height(dashboardPanelHeight)
                    .offset(
                        x = with(density) { pose.translation.x.toDp() },
                        y = with(density) { pose.translation.y.toDp() },
                        z = with(density) { pose.translation.z.toDp() },
                    )
                    .rotate(pose.rotation)
            } ?: SubspaceModifier
                .width(dashboardPanelWidth)
                .height(dashboardPanelHeight)

        SpatialMainPanel(
            modifier = dashboardPanelModifier
                .onSubspaceGloballyPositioned { coordinates ->
                    if (AppState.dashboardPanelPose.value == null) {
                        AppState.updateDashboardPanelPose(coordinates.poseInRoot)
                    }
                }
                .movable(
                    movePolicy = MovePolicy.default { event ->
                        AppState.updateDashboardPanelPose(event.pose)
                    },
                )
                .resizable(
                    minimumSize = DpVolumeSize(
                        width = 760.dp,
                        height = 480.dp,
                        depth = 0.dp,
                    ),
                    resizePolicy = ResizePolicy.custom { event ->
                        val newSize = event.size
                        if (newSize.width > 0 && newSize.height > 0) {
                            dashboardPanelWidth = with(density) { newSize.width.toDp() }
                            dashboardPanelHeight = with(density) { newSize.height.toDp() }
                            AppState.updateDashboardPanelSize(
                                widthDp = dashboardPanelWidth.value,
                                heightDp = dashboardPanelHeight.value,
                            )
                        }
                    },
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
                val dashboardContentWidth = if (useIntegratedSourceRail) {
                    (maxWidth - 140.dp).coerceAtLeast(0.dp)
                } else {
                    maxWidth
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (useIntegratedSourceRail) {
                        DashboardSourceRail(
                            selectedMode = primaryDestination,
                            onModeSelected = ::selectPrimaryDestination,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }

                    AnimatedContent(
                        targetState = DashboardContentState(
                            destination = primaryDestination,
                            workspaceMode = fileWorkspaceMode,
                            isLocalConnected = isLocalModeConnected,
                            isSmbConnected = isSmbModeConnected,
                            selectedSource = uiState.selectedServer,
                        ),
                        transitionSpec = {
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = 220,
                                    delayMillis = 60,
                                )
                            ) togetherWith fadeOut(
                                animationSpec = tween(durationMillis = 140)
                            )
                        },
                        label = "dashboard-content",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { contentState ->
                    val contentSourceTitle = resolveSourceTitle(
                        destination = contentState.destination,
                        selectedServer = contentState.selectedSource,
                    )
                    val contentSourceBreadcrumb = resolveSourceBreadcrumb(
                        destination = contentState.destination,
                        selectedServer = contentState.selectedSource,
                        currentPath = uiState.currentPath,
                        localRootLabel = localFolderName,
                    )
                    when (contentState.destination) {
                        PrimaryDestination.YouTube -> {
                            YouTubePanel(
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PrimaryDestination.Home -> {
                            HomePanel(
                                recentVideos = allRecentVideos,
                                quickAccessFolders = quickAccessFolders,
                                favorites = allFavorites,
                                servers = availableSmbSources,
                                selectedSource = uiState.selectedServer,
                                viewModel = viewModel,
                                metadataRefreshToken = uiState.fileMetadataRefreshToken,
                                onContinueClick = { video ->
                                    openHomeVideo(video)
                                },
                                onContinueRemove = { video ->
                                    dashboardScope.launch {
                                        viewModel.removeFromRecent(video)
                                    }
                                },
                                onQuickAccessClick = { folder ->
                                    clearAllPreviewItems()
                                    fileWorkspaceMode = FileWorkspaceMode.Browser
                                    primaryDestination = PrimaryDestination.SmbFiles
                                    viewModel.openQuickAccess(folder)
                                },
                                onQuickAccessDelete = viewModel::removeQuickAccess,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PrimaryDestination.Library -> {
                            LibraryDestinationPanel(
                                favorites = allFavorites,
                                viewModel = viewModel,
                                metadataRefreshToken = uiState.fileMetadataRefreshToken,
                                dashboardContentWidth = dashboardContentWidth,
                                previewItem = libraryPreviewItem,
                                onFavoriteClick = { video ->
                                    libraryPreviewItem = null
                                    openHomeVideo(video)
                                },
                                onFavoriteToggle = { filePath, fileName, serverAddress, shareName, isFavorite ->
                                    viewModel.toggleFavoriteEntry(
                                        filePath = filePath,
                                        fileName = fileName,
                                        serverAddress = serverAddress,
                                        shareName = shareName,
                                        currentIsFavorite = isFavorite,
                                    )
                                },
                                onPreviewFocused = { preview ->
                                    libraryPreviewItem = preview
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PrimaryDestination.Settings -> {
                            SettingsPanel(
                                versionInfo = appVersionInfo,
                                servers = servers,
                                selectedServer = uiState.selectedServer,
                                metadataScopes = metadataScopes,
                                isHandTrackingEnabled = uiState.isHandTrackingEnabled,
                                isAvBackgroundIndexingEnabled = uiState.isAvBackgroundIndexingEnabled,
                                activeAction = activeSettingsAction,
                                onHandTrackingChange = viewModel::setHandTrackingEnabled,
                                onAvBackgroundIndexingChange = viewModel::setAvBackgroundIndexingEnabled,
                                onAddCurrentMetadataFolder = viewModel::addCurrentFolderMetadataScope,
                                onMetadataScopeEnabled = viewModel::setMetadataScopeEnabled,
                                onMetadataScopeRecursive = viewModel::setMetadataScopeRecursive,
                                onDeleteMetadataScope = viewModel::deleteMetadataScope,
                                onRescanMetadata = viewModel::rescanAvMetadata,
                                onClearArtwork = {
                                    pendingSettingsAction = SettingsAction.ClearArtwork
                                },
                                onClearThumbnails = {
                                    pendingSettingsAction = SettingsAction.ClearThumbnails
                                },
                                onClearRecentHistory = {
                                    pendingSettingsAction = SettingsAction.ClearRecentHistory
                                },
                                onClearFavorites = {
                                    pendingSettingsAction = SettingsAction.ClearFavorites
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        PrimaryDestination.LocalFiles -> {
                            if (!contentState.isLocalConnected) {
                                LocalSourceLandingPanel(
                                    folderName = localFolderName,
                                    isConnecting = uiState.isConnecting,
                                    onOpenLocalFiles = {
                                        requestLocalStorageConnection()
                                    },
                                    onChangeFolder = {
                                        requestLocalStorageConnection(forcePicker = true)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (contentState.workspaceMode == FileWorkspaceMode.Av) {
                                AvWorkspacePanel(
                                    sourceTitle = contentSourceTitle,
                                    breadcrumb = contentSourceBreadcrumb,
                                    sourceActionContent = {
                                        DashboardHeaderChip(
                                            text = stringResource(R.string.change_folder),
                                            icon = Icons.Filled.FolderOpen,
                                            onClick = {
                                                requestLocalStorageConnection(forcePicker = true)
                                            },
                                        )
                                        DashboardHeaderChip(
                                            text = stringResource(R.string.disconnect),
                                            icon = Icons.Filled.Close,
                                            onClick = ::disconnectActiveSource,
                                        )
                                    },
                                    onToggleAvMode = {
                                        fileWorkspaceMode = FileWorkspaceMode.Browser
                                    },
                                    avLibrary = uiState.avLibrary,
                                    isConnected = true,
                                    backgroundIndexingEnabled = uiState.isAvBackgroundIndexingEnabled,
                                    onSetFilterFamily = viewModel::setAvFilterFamily,
                                    onStudioSelected = viewModel::selectAvStudio,
                                    onCastToggled = viewModel::toggleAvCast,
                                    onReleaseDateSelected = viewModel::selectAvReleaseDate,
                                    onVrFilterSelected = viewModel::selectAvVrFilter,
                                    onClearFilters = viewModel::clearAvFilters,
                                    onPreviousMonth = viewModel::showPreviousAvMonth,
                                    onNextMonth = viewModel::showNextAvMonth,
                                    onWorkSelected = viewModel::selectAvWork,
                                    onMergeCast = viewModel::mergeAvPerformers,
                                    onAddCastAlias = viewModel::addAvPerformerAliases,
                                    onSaveWorkMetadata = viewModel::saveAvWorkMetadata,
                                    onRefreshMetadata = viewModel::rescanAvMetadata,
                                    onPlayPart = { filePath, fileName ->
                                        navController.navigate(
                                            Screen.VideoPlayer.createRoute(filePath, fileName)
                                        ) {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                val previewDensity = LocalDensity.current
                                val defaultPreviewWidth = when {
                                    dashboardContentWidth >= 1500.dp -> 420.dp
                                    dashboardContentWidth >= 1180.dp -> 360.dp
                                    else -> 300.dp
                                }
                                val minPreviewWidth = 280.dp
                                val minPrimaryPaneWidth = 460.dp
                                val dividerWidth = 18.dp
                                val maxPreviewWidth =
                                    (dashboardContentWidth - dividerWidth - minPrimaryPaneWidth)
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

                                val activePreviewItem = browserPreviewItem

                                Row(modifier = Modifier.fillMaxSize()) {
                                    FileBrowserPanel(
                                        files = files,
                                        favoritePaths = favoritePaths,
                                        currentPath = uiState.currentPath,
                                        sourceTitle = contentSourceTitle,
                                        breadcrumb = contentSourceBreadcrumb,
                                        metadataRefreshToken = uiState.fileMetadataRefreshToken,
                                        viewModel = viewModel,
                                        isConnected = true,
                                        isLoading = uiState.isLoadingFiles,
                                        errorMessage = uiState.errorMessage,
                                        viewMode = uiState.fileViewMode,
                                        sortMode = uiState.fileSortMode,
                                        currentPreviewKey = browserPreviewItem?.key,
                                        onFileClick = { file ->
                                            browserPreviewItem = null
                                            viewModel.navigateToFile(file)
                                        },
                                        onBackClick = {
                                            browserPreviewItem = null
                                            viewModel.navigateBack()
                                        },
                                        onToggleViewMode = {
                                            browserPreviewItem = null
                                            viewModel.toggleFileViewMode()
                                        },
                                        onSortModeSelected = { sortMode ->
                                            browserPreviewItem = null
                                            viewModel.setFileSortMode(sortMode)
                                        },
                                        onDeleteFiles = { selectedFiles ->
                                            viewModel.deleteFiles(selectedFiles)
                                        },
                                        onPlayVideo = { filePath, fileName ->
                                            browserPreviewItem = null
                                            navController.navigate(
                                                Screen.VideoPlayer.createRoute(filePath, fileName)
                                            ) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onFavoriteToggle = { file, isFavorite ->
                                            viewModel.toggleFavoriteForFile(file, isFavorite)
                                        },
                                        onPreviewFocused = { preview ->
                                            browserPreviewItem = preview
                                        },
                                        onOpenAv = {
                                            fileWorkspaceMode = FileWorkspaceMode.Av
                                        },
                                        headerActions = {
                                            DashboardHeaderChip(
                                                text = stringResource(R.string.change_folder),
                                                icon = Icons.Filled.FolderOpen,
                                                onClick = {
                                                    requestLocalStorageConnection(forcePicker = true)
                                                },
                                            )
                                            DashboardHeaderChip(
                                                text = stringResource(R.string.disconnect),
                                                icon = Icons.Filled.Close,
                                                onClick = ::disconnectActiveSource,
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )

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
                                        previewItem = activePreviewItem,
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .width(previewWidth)
                                            .fillMaxHeight(),
                                    )
                                }
                            }
                        }

                        PrimaryDestination.SmbFiles -> {
                            if (!contentState.isSmbConnected) {
                                ServerListPanel(
                                    servers = availableSmbSources,
                                    isConnecting = uiState.isConnecting,
                                    onServerClick = { server ->
                                        connectToSource(server)
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
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (contentState.workspaceMode == FileWorkspaceMode.Av) {
                                AvWorkspacePanel(
                                    sourceTitle = contentSourceTitle,
                                    breadcrumb = contentSourceBreadcrumb,
                                    sourceActionContent = {
                                        CompactSourceSwitcherButton(
                                            selectedSource = uiState.selectedServer,
                                            availableSources = availableSmbSources,
                                            onSourceSelected = { server ->
                                                connectToSource(server)
                                            },
                                        )
                                        DashboardHeaderChip(
                                            text = stringResource(R.string.add_current_folder),
                                            icon = Icons.Filled.Add,
                                            onClick = viewModel::addCurrentFolderMetadataScope,
                                        )
                                        DashboardHeaderChip(
                                            text = stringResource(R.string.add_quick_access),
                                            icon = Icons.Filled.Favorite,
                                            onClick = viewModel::addCurrentFolderToQuickAccess,
                                        )
                                        DashboardHeaderChip(
                                            text = stringResource(R.string.disconnect),
                                            icon = Icons.Filled.Close,
                                            onClick = ::disconnectActiveSource,
                                        )
                                    },
                                    onToggleAvMode = {
                                        fileWorkspaceMode = FileWorkspaceMode.Browser
                                    },
                                    avLibrary = uiState.avLibrary,
                                    isConnected = true,
                                    backgroundIndexingEnabled = uiState.isAvBackgroundIndexingEnabled,
                                    onSetFilterFamily = viewModel::setAvFilterFamily,
                                    onStudioSelected = viewModel::selectAvStudio,
                                    onCastToggled = viewModel::toggleAvCast,
                                    onReleaseDateSelected = viewModel::selectAvReleaseDate,
                                    onVrFilterSelected = viewModel::selectAvVrFilter,
                                    onClearFilters = viewModel::clearAvFilters,
                                    onPreviousMonth = viewModel::showPreviousAvMonth,
                                    onNextMonth = viewModel::showNextAvMonth,
                                    onWorkSelected = viewModel::selectAvWork,
                                    onMergeCast = viewModel::mergeAvPerformers,
                                    onAddCastAlias = viewModel::addAvPerformerAliases,
                                    onSaveWorkMetadata = viewModel::saveAvWorkMetadata,
                                    onRefreshMetadata = viewModel::rescanAvMetadata,
                                    onPlayPart = { filePath, fileName ->
                                        navController.navigate(
                                            Screen.VideoPlayer.createRoute(filePath, fileName)
                                        ) {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                val previewDensity = LocalDensity.current
                                val defaultPreviewWidth = when {
                                    dashboardContentWidth >= 1500.dp -> 420.dp
                                    dashboardContentWidth >= 1180.dp -> 360.dp
                                    else -> 300.dp
                                }
                                val minPreviewWidth = 280.dp
                                val minPrimaryPaneWidth = 460.dp
                                val dividerWidth = 18.dp
                                val maxPreviewWidth =
                                    (dashboardContentWidth - dividerWidth - minPrimaryPaneWidth)
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

                                val activePreviewItem = browserPreviewItem

                                Row(modifier = Modifier.fillMaxSize()) {
                                    FileBrowserPanel(
                                        files = files,
                                        favoritePaths = favoritePaths,
                                        currentPath = uiState.currentPath,
                                        sourceTitle = contentSourceTitle,
                                        breadcrumb = contentSourceBreadcrumb,
                                        metadataRefreshToken = uiState.fileMetadataRefreshToken,
                                        viewModel = viewModel,
                                        isConnected = true,
                                        isLoading = uiState.isLoadingFiles,
                                        errorMessage = uiState.errorMessage,
                                        viewMode = uiState.fileViewMode,
                                        sortMode = uiState.fileSortMode,
                                        currentPreviewKey = browserPreviewItem?.key,
                                        onFileClick = { file ->
                                            browserPreviewItem = null
                                            viewModel.navigateToFile(file)
                                        },
                                        onBackClick = {
                                            browserPreviewItem = null
                                            viewModel.navigateBack()
                                        },
                                        onToggleViewMode = {
                                            browserPreviewItem = null
                                            viewModel.toggleFileViewMode()
                                        },
                                        onSortModeSelected = { sortMode ->
                                            browserPreviewItem = null
                                            viewModel.setFileSortMode(sortMode)
                                        },
                                        onDeleteFiles = { selectedFiles ->
                                            viewModel.deleteFiles(selectedFiles)
                                        },
                                        onPlayVideo = { filePath, fileName ->
                                            browserPreviewItem = null
                                            navController.navigate(
                                                Screen.VideoPlayer.createRoute(filePath, fileName)
                                            ) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onFavoriteToggle = { file, isFavorite ->
                                            viewModel.toggleFavoriteForFile(file, isFavorite)
                                        },
                                        onPreviewFocused = { preview ->
                                            browserPreviewItem = preview
                                        },
                                        onOpenAv = {
                                            fileWorkspaceMode = FileWorkspaceMode.Av
                                        },
                                        headerActions = {
                                            CompactSourceSwitcherButton(
                                                selectedSource = uiState.selectedServer,
                                                availableSources = availableSmbSources,
                                                onSourceSelected = { server ->
                                                    connectToSource(server)
                                                },
                                            )
                                            DashboardHeaderChip(
                                                text = stringResource(R.string.add_current_folder),
                                                icon = Icons.Filled.Add,
                                                onClick = viewModel::addCurrentFolderMetadataScope,
                                            )
                                            DashboardHeaderChip(
                                                text = stringResource(R.string.add_quick_access),
                                                icon = Icons.Filled.Favorite,
                                                onClick = viewModel::addCurrentFolderToQuickAccess,
                                            )
                                            DashboardHeaderChip(
                                                text = stringResource(R.string.disconnect),
                                                icon = Icons.Filled.Close,
                                                onClick = ::disconnectActiveSource,
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )

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
                                        previewItem = activePreviewItem,
                                        viewModel = viewModel,
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

        if (!useIntegratedSourceRail) {
            Orbiter(
                position = ContentEdge.Top,
                offset = 24.dp,
                offsetType = OrbiterOffsetType.InnerEdge,
                alignment = Alignment.CenterHorizontally,
                elevation = 16.dp,
                shouldRenderInNonSpatial = true,
            ) {
                DashboardSourceBar(
                    selectedMode = primaryDestination,
                    onModeSelected = ::selectPrimaryDestination,
                )
            }
        }

        // Add/Edit Server Dialog
        if (showAddServerDialog || serverToEdit != null) {
            AddServerDialog(
                initialServer = serverToEdit,
                initialCredentials = serverToEdit
                    ?.let(viewModel::loadCredentials)
                    ?: SmbCredentials(),
                onDismiss = {
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onSave = { server, credentials ->
                    if (serverToEdit != null) {
                        viewModel.updateServer(server, credentials)
                    } else {
                        viewModel.addServer(server, credentials)
                    }
                    showAddServerDialog = false
                    serverToEdit = null
                },
                onTestConnection = { server, credentials ->
                    viewModel.testConnection(server, credentials)
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
                            text = stringResource(R.string.connecting_to_source),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }

    pendingSettingsAction?.let { action ->
        val titleRes = when (action) {
            SettingsAction.ClearArtwork -> R.string.clear_artwork_confirm_title
            SettingsAction.ClearThumbnails -> R.string.clear_thumbnails_confirm_title
            SettingsAction.ClearRecentHistory -> R.string.clear_recent_history_confirm_title
            SettingsAction.ClearFavorites -> R.string.clear_favorites_confirm_title
        }
        val messageRes = when (action) {
            SettingsAction.ClearArtwork -> R.string.clear_artwork_confirm_message
            SettingsAction.ClearThumbnails -> R.string.clear_thumbnails_confirm_message
            SettingsAction.ClearRecentHistory -> R.string.clear_recent_history_confirm_message
            SettingsAction.ClearFavorites -> R.string.clear_favorites_confirm_message
        }

        AlertDialog(
            onDismissRequest = { pendingSettingsAction = null },
            title = {
                Text(stringResource(titleRes))
            },
            text = {
                Text(stringResource(messageRes))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSettingsAction = null
                        launchSettingsAction(action)
                    },
                ) {
                    Text(stringResource(R.string.settings_action_clear), color = NetflixRed)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingSettingsAction = null },
                ) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
        )
    }

    settingsFeedback?.let { feedback ->
        val message = if (feedback.detail == null) {
            when (feedback.action) {
                SettingsAction.ClearArtwork -> stringResource(R.string.clear_artwork_complete_message)
                SettingsAction.ClearThumbnails -> stringResource(R.string.clear_thumbnails_complete_message)
                SettingsAction.ClearRecentHistory -> stringResource(R.string.clear_recent_history_complete_message)
                SettingsAction.ClearFavorites -> stringResource(R.string.clear_favorites_complete_message)
            }
        } else {
            feedback.detail
        }

        AlertDialog(
            onDismissRequest = { settingsFeedback = null },
            title = {
                Text(
                    stringResource(
                        if (feedback.detail == null) {
                            R.string.settings_action_complete_title
                        } else {
                            R.string.settings_action_failed_title
                        }
                    )
                )
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsFeedback = null },
                ) {
                    Text(stringResource(R.string.ok), color = NetflixRed)
                }
            },
        )
    }

    if (shouldShowControllerHandTrackingPrompt) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setControllerHandTrackingPromptHandled()
            },
            title = {
                Text(stringResource(R.string.controller_detected_hand_tracking_title))
            },
            text = {
                Text(stringResource(R.string.controller_detected_hand_tracking_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setHandTrackingEnabled(false)
                        viewModel.setControllerHandTrackingPromptHandled()
                    },
                ) {
                    Text(
                        stringResource(R.string.disable_hand_tracking_action),
                        color = NetflixRed,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setControllerHandTrackingPromptHandled()
                    },
                ) {
                    Text(
                        stringResource(R.string.keep_hand_tracking_action),
                        color = TextSecondary,
                    )
                }
            },
        )
    }
}

@Composable
private fun DashboardOrbitArea(
    selectedSource: SavedServer?,
    availableSources: List<SavedServer>,
    onSourceSelected: (SavedServer) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            SourceSwitcherButton(
                selectedSource = selectedSource,
                availableSources = availableSources,
                onSourceSelected = onSourceSelected,
                modifier = Modifier.weight(1f),
            )

            TextButton(onClick = onDisconnect) {
                Text(stringResource(R.string.disconnect), color = NetflixRed)
            }
        }
    }
}

private data class VirtualVideoGroup(
    val key: String,
    val files: List<SMBFileItem>,
    val representativeFile: SMBFileItem?,
) {
    val fileCount: Int
        get() = files.size
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
private fun rememberVideoFileMetadata(
    viewModel: MainDashboardViewModel,
    file: SMBFileItem,
    isVideoFile: Boolean,
    preferredBaseName: String? = null,
    refreshToken: Long,
): ArtworkState<JvrMovieMetadata> {
    if (!isVideoFile) return ArtworkState.Missing

    val lookupRequest = remember(file.path, preferredBaseName) {
        buildVideoMetadataLookupRequest(file.path)
            ?: preferredBaseName
                ?.let(::extractNormalizedCodeFromFileName)
                ?.let { code ->
                    VideoMetadataLookupRequest(
                        code = code,
                        folderPath = file.path.substringBeforeLast('/', ""),
                    )
                }
    }

    val metadataState = produceState<ArtworkState<JvrMovieMetadata>>(
        initialValue = ArtworkState.Loading,
        file.path,
        file.lastModified,
        preferredBaseName,
        refreshToken,
    ) {
        val resolvedMetadata = viewModel.resolveBrowserFileMetadata(file, preferredBaseName)
        if (resolvedMetadata == null) {
            logMetadataTrace(
                FILE_THUMBNAIL_LOG_TAG,
                "No metadata for file=${file.path} code=${lookupRequest?.code ?: "<unknown>"}. Falling back to generated video thumbnail."
            )
        } else {
            logMetadataTrace(
                FILE_THUMBNAIL_LOG_TAG,
                "Metadata loaded for file=${file.path} code=${lookupRequest?.code ?: resolvedMetadata.code} posterUrl=${resolvedMetadata.posterUrl ?: "<none>"}"
            )
        }
        value = resolvedMetadata
            ?.let { ArtworkState.Resolved(it) }
            ?: ArtworkState.Missing
    }

    return metadataState.value
}

@Composable
private fun rememberGroupMetadata(
    viewModel: MainDashboardViewModel,
    group: VirtualVideoGroup,
    refreshToken: Long,
): ArtworkState<JvrMovieMetadata> {
    val normalizedCode = remember(group.key) { group.key.trim().uppercase() }
    val representativePath = group.representativeFile?.path

    val metadataState = produceState<ArtworkState<JvrMovieMetadata>>(
        initialValue = ArtworkState.Loading,
        group.key,
        representativePath,
        refreshToken,
    ) {
        val metadata = viewModel.resolveBrowserGroupMetadata(
            groupKey = group.key,
            representativeFile = group.representativeFile,
            groupFiles = group.files,
        )

        if (metadata == null) {
            logMetadataTrace(
                GROUP_THUMBNAIL_LOG_TAG,
                "No metadata for group=$normalizedCode. Falling back to generated video thumbnail."
            )
        } else {
            logMetadataTrace(
                GROUP_THUMBNAIL_LOG_TAG,
                "Metadata loaded for group=$normalizedCode posterUrl=${metadata.posterUrl ?: "<none>"}"
            )
        }

        value = metadata
            ?.let { ArtworkState.Resolved(it) }
            ?: ArtworkState.Missing
    }
    return metadataState.value
}

@Composable
private fun rememberBrowserFolderArtwork(
    viewModel: MainDashboardViewModel,
    folder: SMBFileItem,
    kind: BrowserFolderArtworkKind,
    refreshToken: Long,
): ArtworkState<BrowserFolderArtworkResolution> {
    if (!folder.isDirectory) return ArtworkState.Missing

    val artworkState = produceState<ArtworkState<BrowserFolderArtworkResolution>>(
        initialValue = ArtworkState.Loading,
        folder.path,
        folder.lastModified,
        kind,
        refreshToken,
    ) {
        val resolution = viewModel.resolveBrowserFolderArtwork(folder, kind)
        value = if (
            resolution.metadata != null ||
            resolution.representativeVideo != null ||
            !resolution.actorArtworkUrl.isNullOrBlank()
        ) {
            ArtworkState.Resolved(resolution)
        } else {
            ArtworkState.Missing
        }
    }
    return artworkState.value
}

/**
 * LEFT PANEL: Server List
 */
@Composable
private fun ServerListPanel(
    servers: List<SavedServer>,
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
                    text = stringResource(R.string.smb_files),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onAddServerClick,
                    enabled = !isConnecting
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_server),
                        tint = NetflixRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            // SMB server list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

@Composable
private fun DashboardWorkspaceHeader(
    title: String,
    summary: String?,
    summaryColor: Color = TextTertiary,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        summary?.takeIf { it.isNotBlank() }?.let { value ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}

@Composable
private fun AvWorkspacePanel(
    sourceTitle: String,
    breadcrumb: String?,
    sourceActionContent: @Composable RowScope.() -> Unit,
    onToggleAvMode: () -> Unit,
    avLibrary: AvLibraryState,
    isConnected: Boolean,
    backgroundIndexingEnabled: Boolean,
    onSetFilterFamily: (AvFilterFamily) -> Unit,
    onStudioSelected: (String?) -> Unit,
    onCastToggled: (String) -> Unit,
    onReleaseDateSelected: (LocalDate?) -> Unit,
    onVrFilterSelected: (AvVrFilterOption) -> Unit,
    onClearFilters: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onWorkSelected: (String?) -> Unit,
    onMergeCast: (String, String) -> Unit,
    onAddCastAlias: (String, String?, String?) -> Unit,
    onSaveWorkMetadata: (String, JvrMovieMetadata) -> Unit,
    onRefreshMetadata: () -> Unit,
    onPlayPart: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = CardBackground,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            DashboardWorkspaceHeader(
                title = sourceTitle,
                summary = breadcrumb,
            ) {
                sourceActionContent()
                DashboardHeaderChip(
                    text = "AV",
                    icon = Icons.Filled.MovieCreation,
                    selected = true,
                    onClick = onToggleAvMode,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(16.dp))

            AvLibraryPanel(
                avLibrary = avLibrary,
                isConnected = isConnected,
                backgroundIndexingEnabled = backgroundIndexingEnabled,
                onSetFilterFamily = onSetFilterFamily,
                onStudioSelected = onStudioSelected,
                onCastToggled = onCastToggled,
                onReleaseDateSelected = onReleaseDateSelected,
                onVrFilterSelected = onVrFilterSelected,
                onClearFilters = onClearFilters,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onWorkSelected = onWorkSelected,
                onMergeCast = onMergeCast,
                onAddCastAlias = onAddCastAlias,
                onSaveWorkMetadata = onSaveWorkMetadata,
                onRefreshMetadata = onRefreshMetadata,
                onPlayPart = onPlayPart,
                showHeader = false,
                showContainer = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * CENTER PANEL: File Browser
 */
internal fun resolveChildFolderArtworkKind(currentPath: String): BrowserFolderArtworkKind {
    val currentFolderName = resolveCurrentFolderBaseName(currentPath)
    return if (currentFolderName.equals("japan", ignoreCase = true)) {
        BrowserFolderArtworkKind.ACTOR
    } else {
        BrowserFolderArtworkKind.CONTENT
    }
}

internal fun resolveCurrentFolderBaseName(currentPath: String): String? {
    val encodedLastSegment = currentPath
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .trimEnd('/')
        .substringAfterLast('/')
        .takeIf { it.isNotBlank() }
        ?: return null
    val decoded = runCatching {
        URLDecoder.decode(encodedLastSegment, StandardCharsets.UTF_8.name())
    }.getOrDefault(encodedLastSegment)
    return decoded
        .replace('\\', '/')
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()
        .takeIf { it.isNotBlank() }
}

@Composable
private fun FileBrowserPanel(
    files: List<SMBFileItem>,
    favoritePaths: Set<String>,
    currentPath: String,
    sourceTitle: String,
    breadcrumb: String?,
    metadataRefreshToken: Long,
    viewModel: MainDashboardViewModel,
    isConnected: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    viewMode: FileBrowserViewMode,
    sortMode: FileBrowserSortMode,
    currentPreviewKey: String?,
    onFileClick: (SMBFileItem) -> Unit,
    onBackClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onSortModeSelected: (FileBrowserSortMode) -> Unit,
    onDeleteFiles: suspend (List<SMBFileItem>) -> Result<Int>,
    onPlayVideo: (String, String) -> Unit,
    onFavoriteToggle: (SMBFileItem, Boolean) -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
    onOpenAv: () -> Unit,
    headerActions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleteInProgress by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activeVirtualGroupKey by rememberSaveable(currentPath) { mutableStateOf<String?>(null) }
    val folderArtworkKind = remember(currentPath) {
        resolveChildFolderArtworkKind(currentPath)
    }
    val currentFolderBaseName = remember(currentPath) {
        resolveCurrentFolderBaseName(currentPath)
    }

    val virtualGroups = remember(files) { buildVirtualVideoGroups(files) }
    val activeVirtualGroup = remember(virtualGroups, activeVirtualGroupKey) {
        activeVirtualGroupKey?.let(virtualGroups::get)
    }
    val activeVirtualGroupMetadataState = if (activeVirtualGroup != null) {
        rememberGroupMetadata(
            viewModel = viewModel,
            group = activeVirtualGroup,
            refreshToken = metadataRefreshToken,
        )
    } else {
        ArtworkState.Missing
    }
    val displayItems = remember(files, virtualGroups, activeVirtualGroupKey) {
        buildFileBrowserDisplayItems(files, virtualGroups, activeVirtualGroupKey)
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
    val canNavigateUp =
        (activeVirtualGroup != null || (currentPath.isNotEmpty() && currentPath != "/")) && !isDeleteMode
    val navigateUpLabel =
        if (activeVirtualGroup != null) {
            stringResource(R.string.back_to_folder_list)
        } else {
            stringResource(R.string.back_to_parent_folder)
        }
    val headerSummary = when {
        isDeleteMode -> stringResource(
            R.string.files_selected_count,
            selectedPaths.size,
        )

        activeVirtualGroup != null && displayedPath.isNotBlank() -> displayedPath
        !breadcrumb.isNullOrBlank() -> breadcrumb
        isConnected && displayedPath.isNotBlank() -> displayedPath
        else -> null
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

    fun handleBrowserBack(): Boolean {
        if (showDeleteConfirmDialog) {
            if (!isDeleteInProgress) {
                showDeleteConfirmDialog = false
            }
            return true
        }

        if (isDeleteMode) {
            isDeleteMode = false
            selectedPaths = emptySet()
            return true
        }

        if (activeVirtualGroupKey != null || (currentPath.isNotEmpty() && currentPath != "/")) {
            handleBackAction()
            return true
        }

        return false
    }

    val backHandlerState = rememberUpdatedState(newValue = { handleBrowserBack() })
    val shouldHandleSystemBack =
        showDeleteConfirmDialog ||
                isDeleteMode ||
                activeVirtualGroupKey != null ||
                (currentPath.isNotEmpty() && currentPath != "/")

    BackHandler(enabled = shouldHandleSystemBack) {
        backHandlerState.value.invoke()
    }

    LaunchedEffect(Unit) {
        AppState.keyEvents.collect { event ->
            if (
                event.action == KeyEvent.ACTION_UP &&
                event.keyCode == KeyEvent.KEYCODE_BUTTON_B
            ) {
                backHandlerState.value.invoke()
            }
        }
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
            DashboardWorkspaceHeader(
                title = sourceTitle,
                summary = headerSummary,
                summaryColor = if (isDeleteMode && selectedPaths.isNotEmpty()) {
                    NetflixRed
                } else {
                    TextTertiary
                },
            ) {
                headerActions()

                    DashboardHeaderChip(
                        text = "AV",
                        icon = Icons.Filled.MovieCreation,
                        onClick = {
                            if (!isDeleteMode && !isDeleteInProgress) {
                                onPreviewFocused(null)
                                onOpenAv()
                            }
                        },
                    )

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
                                stringResource(R.string.switch_to_thumbnail_view)
                            } else {
                                stringResource(R.string.switch_to_list_view)
                            },
                            tint = if (isDeleteMode) TextTertiary else TextSecondary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            enabled = isConnected && !isLoading && !isDeleteMode && !isDeleteInProgress,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_files),
                                tint = if (showSortMenu) NetflixRed else TextSecondary,
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
                            shape = RoundedCornerShape(20.dp),
                            containerColor = CardBackgroundHover.copy(alpha = 0.98f),
                            tonalElevation = 8.dp,
                            shadowElevation = 18.dp,
                            border = BorderStroke(1.dp, DividerGray.copy(alpha = 0.85f)),
                        ) {
                            FileBrowserSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = fileBrowserSortLabel(mode),
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = fileBrowserSortIcon(mode),
                                            contentDescription = null,
                                            tint = fileBrowserSortAccent(mode),
                                        )
                                    },
                                    trailingIcon = {
                                        if (mode == sortMode) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                            )
                                        }
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        onSortModeSelected(mode)
                                    },
                                )
                            }
                        }
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
                                stringResource(R.string.confirm_selected_file_deletion)
                            } else {
                                stringResource(R.string.select_files_to_delete)
                            },
                            tint = if (isDeleteMode) NetflixRed else TextSecondary
                        )
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
                        message = stringResource(R.string.connect_to_browse_files)
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
                    if (canNavigateUp) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NavigateUpListCard(
                                title = navigateUpLabel,
                                onClick = ::handleBackAction,
                            )
                            EmptyState(
                                icon = Icons.Filled.FolderOpen,
                                message = stringResource(R.string.no_files_in_directory),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    } else {
                        EmptyState(
                            icon = Icons.Filled.FolderOpen,
                            message = stringResource(R.string.no_files_in_directory)
                        )
                    }
                }

                else -> {
                    when (viewMode) {
                        FileBrowserViewMode.List -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (canNavigateUp) {
                                    item(key = "navigate-up") {
                                        NavigateUpListCard(
                                            title = navigateUpLabel,
                                            onClick = ::handleBackAction,
                                        )
                                    }
                                }
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    viewModel = viewModel,
                                                    group = item.virtualGroup,
                                                    refreshToken = metadataRefreshToken,
                                                )
                                            VirtualGroupListCard(
                                                group = item.virtualGroup,
                                                metadataState = groupMetadata,
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
                                            val isFavorite = favoritePaths.contains(file.path)
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileListEntryCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                folderArtworkKind = folderArtworkKind,
                                                preferredMetadataBaseName = if (isVideoFile) {
                                                    resolveParentFolderBaseName(file.path)
                                                        ?: currentFolderBaseName
                                                } else {
                                                    currentFolderBaseName
                                                },
                                                metadataRefreshToken = metadataRefreshToken,
                                                viewModel = viewModel,
                                                sharedMetadataState = if (item.isVirtualGroupMember) {
                                                    activeVirtualGroupMetadataState
                                                } else {
                                                    null
                                                },
                                                sharedArtworkVideoPath = if (item.isVirtualGroupMember) {
                                                    activeVirtualGroup?.representativeFile?.path
                                                } else {
                                                    null
                                                },
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
                                if (canNavigateUp) {
                                    item(
                                        key = "navigate-up",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        NavigateUpListCard(
                                            title = navigateUpLabel,
                                            onClick = ::handleBackAction,
                                        )
                                    }
                                }
                                items(
                                    items = displayItems,
                                    key = { item -> item.stableKey }
                                ) { item ->
                                    when (item) {
                                        is FileBrowserDisplayItem.Group -> {
                                            val groupMetadata =
                                                rememberGroupMetadata(
                                                    viewModel = viewModel,
                                                    group = item.virtualGroup,
                                                    refreshToken = metadataRefreshToken,
                                                )
                                            VirtualGroupThumbnailCard(
                                                group = item.virtualGroup,
                                                metadataState = groupMetadata,
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
                                            val isFavorite = favoritePaths.contains(file.path)
                                            val isSelected = selectedPaths.contains(file.path)

                                            FileThumbnailCard(
                                                file = file,
                                                isVideoFile = isVideoFile,
                                                folderArtworkKind = folderArtworkKind,
                                                preferredMetadataBaseName = if (isVideoFile) {
                                                    resolveParentFolderBaseName(file.path)
                                                        ?: currentFolderBaseName
                                                } else {
                                                    currentFolderBaseName
                                                },
                                                metadataRefreshToken = metadataRefreshToken,
                                                viewModel = viewModel,
                                                sharedMetadataState = if (item.isVirtualGroupMember) {
                                                    activeVirtualGroupMetadataState
                                                } else {
                                                    null
                                                },
                                                sharedArtworkVideoPath = if (item.isVirtualGroupMember) {
                                                    activeVirtualGroup?.representativeFile?.path
                                                } else {
                                                    null
                                                },
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
                Text(stringResource(R.string.delete_selected_files_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.delete_selected_files_message,
                        selectedPaths.size,
                    )
                )
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
                        Text(stringResource(R.string.delete), color = NetflixRed)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleteInProgress
                ) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun fileBrowserSortLabel(sortMode: FileBrowserSortMode): String = when (sortMode) {
    FileBrowserSortMode.Newest -> stringResource(R.string.sort_newest)
    FileBrowserSortMode.Oldest -> stringResource(R.string.sort_oldest)
    FileBrowserSortMode.FilenameAscending -> stringResource(R.string.sort_filename_ascending)
    FileBrowserSortMode.FilenameDescending -> stringResource(R.string.sort_filename_descending)
}

private fun fileBrowserSortIcon(sortMode: FileBrowserSortMode): ImageVector = when (sortMode) {
    FileBrowserSortMode.Newest -> Icons.Filled.South
    FileBrowserSortMode.Oldest -> Icons.Filled.North
    FileBrowserSortMode.FilenameAscending,
    FileBrowserSortMode.FilenameDescending -> Icons.Filled.SortByAlpha
}

private fun fileBrowserSortAccent(sortMode: FileBrowserSortMode): Color = when (sortMode) {
    FileBrowserSortMode.Newest,
    FileBrowserSortMode.Oldest -> NetflixRed

    FileBrowserSortMode.FilenameAscending,
    FileBrowserSortMode.FilenameDescending -> AccentGold
}

private data class GroupHoverPreviewSpec(
    val model: Any,
    val diskCacheKey: String?,
    val source: String,
    val trailerPath: String? = null,
    val extraFanartLookupPath: String? = null,
    val fallbackVideoPath: String? = null,
)

private fun buildGroupHoverPreviewSpec(
    shouldUsePoster: Boolean,
    posterUrl: String?,
    posterCacheKey: String?,
    representativePath: String?,
    previewVideoPath: String? = representativePath,
): GroupHoverPreviewSpec? {
    val resolvedPreviewVideoPath = previewVideoPath ?: representativePath
    val usesTrailer = !previewVideoPath.isNullOrBlank() && previewVideoPath != representativePath
    val trailerPath = resolvedPreviewVideoPath.takeIf { usesTrailer }
    val extraFanartLookupPath = representativePath.takeIf { !usesTrailer }
    val fallbackVideoPath = representativePath.takeIf { !usesTrailer }
    return when {
        shouldUsePoster && !posterUrl.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = posterUrl,
            diskCacheKey = posterCacheKey,
            source = "poster",
            trailerPath = trailerPath,
            extraFanartLookupPath = extraFanartLookupPath,
            fallbackVideoPath = fallbackVideoPath,
        )

        !representativePath.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = VideoThumbnailFetcher.Model(
                representativePath,
            ),
            diskCacheKey = VideoThumbnailFetcher.diskCacheKey(
                representativePath,
            ),
            source = "generated",
            trailerPath = trailerPath,
            extraFanartLookupPath = extraFanartLookupPath,
            fallbackVideoPath = fallbackVideoPath,
        )

        else -> null
    }
}


private fun buildLibraryPreviewSpec(
    video: LibraryVideoItem,
    generatedFramePath: String?,
): GroupHoverPreviewSpec? {
    return when {
        !generatedFramePath.isNullOrBlank() -> GroupHoverPreviewSpec(
            model = generatedFramePath,
            diskCacheKey = generatedFramePath,
            source = "cached",
            extraFanartLookupPath = video.filePath,
            fallbackVideoPath = video.filePath,
        )

        video.filePath.isNotBlank() -> GroupHoverPreviewSpec(
            model = VideoThumbnailFetcher.Model(
                video.filePath,
            ),
            diskCacheKey = VideoThumbnailFetcher.diskCacheKey(
                video.filePath,
            ),
            source = "generated",
            extraFanartLookupPath = video.filePath,
            fallbackVideoPath = video.filePath,
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
    metadataState: ArtworkState<JvrMovieMetadata>,
    currentPreviewKey: String? = null,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)
    val metadata = metadataState.resolvedValueOrNull()

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }
    val displayDateText = formatFileBrowserDate(resolveDisplayDate(group, metadata))

    val representativePath = group.representativeFile?.path
    val previewVideoPath = group.representativeFile?.trailerPath ?: representativePath
    var posterFailureCount by remember(
        group.key,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = selectVideoArtwork(
        metadataState = metadataState,
        posterFailureCount = posterFailureCount,
        videoPath = representativePath,
    )
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }
    val shouldUsePoster = artworkSelection is VideoArtworkSelection.Poster
    val shouldUseGeneratedFrame = artworkSelection is VideoArtworkSelection.GeneratedFrame
    val previewSpec = remember(
        artworkSelection,
        posterUrl,
        posterCacheKey,
        representativePath,
        previewVideoPath,
    ) {
        if (artworkSelection is VideoArtworkSelection.Placeholder) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = representativePath,
                previewVideoPath = previewVideoPath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        secondaryText,
        previewSpec,
        metadata,
        shouldUsePoster,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "group:${representativePath ?: group.key}",
            title = displayTitle,
            subtitle = secondaryText,
            previewSpec = previewSpec,
            fallbackPreviewSpec = if (shouldUsePoster) {
                buildGroupHoverPreviewSpec(
                    shouldUsePoster = false,
                    posterUrl = null,
                    posterCacheKey = null,
                    representativePath = representativePath,
                    previewVideoPath = previewVideoPath,
                )
            } else {
                null
            },
            metadata = metadata,
            metadataLookupRequest = null,
            onPreviewLoadError = if (shouldUsePoster) {
                { posterFailureCount += 1 }
            } else {
                null
            },
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key
    LaunchedEffect(isPreviewFocused, previewSpec, metadata) {
        if (isPreviewFocused) onPreviewFocused(previewItem)
    }

    LaunchedEffect(group.key, metadataState, posterFailureCount) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            shouldUseGeneratedFrame -> "generated-video-frame"
            metadataState is ArtworkState.Loading -> "metadata-loading"
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
            "Render group=${group.key} source=$source posterFailed=$posterFailureCount posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
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
                                        posterFailureCount += 1
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    shouldUseGeneratedFrame && !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(
                                    VideoThumbnailFetcher.Model(
                                        path = representativePath,
                                    )
                                )
                                .diskCacheKey(
                                    VideoThumbnailFetcher.diskCacheKey(
                                        path = representativePath,
                                    )
                                )
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
                displayDateText?.let { dateText ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
private fun NavigateUpListCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(10.dp),
        border = if (isHovered || isFocused) {
            BorderStroke(1.dp, NetflixRed.copy(alpha = 0.85f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DividerGray.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = TextPrimary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun VirtualGroupThumbnailCard(
    group: VirtualVideoGroup,
    metadataState: ArtworkState<JvrMovieMetadata>,
    currentPreviewKey: String? = null,
    onClick: () -> Unit,
    onPopupClick: () -> Unit = onClick,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ThumbnailImageLoaderProvider.get(context)
    val metadata = metadataState.resolvedValueOrNull()

    val displayTitle = metadata?.title ?: group.key
    val secondaryText = if (metadata != null) {
        "${group.key} · ${group.fileCount} part files"
    } else {
        "${group.fileCount} part files"
    }
    val displayDateText = formatFileBrowserDate(resolveDisplayDate(group, metadata))

    val representativePath = group.representativeFile?.path
    val previewVideoPath = group.representativeFile?.trailerPath ?: representativePath
    var posterFailureCount by remember(
        group.key,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = selectVideoArtwork(
        metadataState = metadataState,
        posterFailureCount = posterFailureCount,
        videoPath = representativePath,
    )
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val posterCacheKey = posterUrl?.let { buildGroupPosterCacheKey(group.key, it) }
    val shouldUsePoster = artworkSelection is VideoArtworkSelection.Poster
    val shouldUseGeneratedFrame = artworkSelection is VideoArtworkSelection.GeneratedFrame
    val previewSpec = remember(
        artworkSelection,
        posterUrl,
        posterCacheKey,
        representativePath,
        previewVideoPath,
    ) {
        if (artworkSelection is VideoArtworkSelection.Placeholder) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = representativePath,
                previewVideoPath = previewVideoPath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        secondaryText,
        previewSpec,
        metadata,
        shouldUsePoster,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "group:${representativePath ?: group.key}",
            title = displayTitle,
            subtitle = secondaryText,
            previewSpec = previewSpec,
            fallbackPreviewSpec = if (shouldUsePoster) {
                buildGroupHoverPreviewSpec(
                    shouldUsePoster = false,
                    posterUrl = null,
                    posterCacheKey = null,
                    representativePath = representativePath,
                    previewVideoPath = previewVideoPath,
                )
            } else {
                null
            },
            metadata = metadata,
            metadataLookupRequest = null,
            onPreviewLoadError = if (shouldUsePoster) {
                { posterFailureCount += 1 }
            } else {
                null
            },
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key
    LaunchedEffect(isPreviewFocused, previewSpec, metadata) {
        if (isPreviewFocused) onPreviewFocused(previewItem)
    }

    LaunchedEffect(group.key, metadataState, posterFailureCount) {
        val source = when {
            shouldUsePoster -> "remote-poster"
            shouldUseGeneratedFrame -> "generated-video-frame"
            metadataState is ArtworkState.Loading -> "metadata-loading"
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
            "Render group=${group.key} source=$source posterFailed=$posterFailureCount posterUrl=${posterUrl ?: "<none>"} posterCacheKey=${posterCacheKey ?: "<none>"} posterCached=$posterCached representativePath=${representativePath ?: "<none>"}"
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
                                        posterFailureCount += 1
                                    }
                                )
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = "Poster thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    shouldUseGeneratedFrame && !representativePath.isNullOrBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(
                                    VideoThumbnailFetcher.Model(
                                        path = representativePath,
                                    )
                                )
                                .diskCacheKey(
                                    VideoThumbnailFetcher.diskCacheKey(
                                        path = representativePath,
                                    )
                                )
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
            displayDateText?.let { dateText ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    folderArtworkKind: BrowserFolderArtworkKind,
    preferredMetadataBaseName: String?,
    metadataRefreshToken: Long,
    viewModel: MainDashboardViewModel,
    sharedMetadataState: ArtworkState<JvrMovieMetadata>?,
    sharedArtworkVideoPath: String?,
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
    val usesSharedMetadata = sharedMetadataState != null
    val artworkVideoPath = sharedArtworkVideoPath ?: file.path
    val previewVideoPath = file.trailerPath ?: artworkVideoPath
    val metadataState = if (sharedMetadataState != null) {
        sharedMetadataState
    } else {
        rememberVideoFileMetadata(
            viewModel = viewModel,
            file = file,
            isVideoFile = isVideoFile,
            preferredBaseName = preferredMetadataBaseName,
            refreshToken = metadataRefreshToken,
        )
    }
    val metadata = metadataState.resolvedValueOrNull()
    val folderArtworkState = rememberBrowserFolderArtwork(
        viewModel = viewModel,
        folder = file,
        kind = folderArtworkKind,
        refreshToken = metadataRefreshToken,
    )
    val folderResolution = folderArtworkState.resolvedValueOrNull()
    var folderArtworkFailureCount by remember(
        file.path,
        folderResolution?.metadata?.posterUrl,
        folderResolution?.metadata?.posterFallbackUrls,
        folderResolution?.actorArtworkUrl,
    ) { mutableStateOf(0) }
    val folderArtworkSelection = selectFolderArtwork(
        artworkState = folderArtworkState,
        imageFailureCount = folderArtworkFailureCount,
    )
    val folderImageUrl = when (folderArtworkSelection) {
        is FolderArtworkSelection.Poster -> folderArtworkSelection.url
        is FolderArtworkSelection.ActorImage -> folderArtworkSelection.url
        else -> null
    }
    val shouldUseFolderImage = folderImageUrl != null
    val folderRepresentativePath = folderResolution?.representativeVideo?.path
    val shouldUseFolderGeneratedFrame =
        folderArtworkSelection is FolderArtworkSelection.GeneratedFrame
    val folderGeneratedThumbnailModel = folderRepresentativePath?.let { path ->
        VideoThumbnailFetcher.Model(path = path)
    }
    val folderGeneratedThumbnailDiskCacheKey = folderRepresentativePath?.let { path ->
        VideoThumbnailFetcher.diskCacheKey(path = path)
    }
    val folderPosterCacheKey = if (folderArtworkSelection is FolderArtworkSelection.Poster) {
        buildFilePosterCacheKey(file.name, folderArtworkSelection.url)
    } else {
        null
    }
    val displayTitle =
        if (!usesSharedMetadata && isVideoFile) metadata?.title ?: file.name else file.name
    val subtitleText = if (!usesSharedMetadata) metadata?.code ?: file.name else file.name
    val detailText = if (!file.isDirectory) {
        buildFileBrowserDetailText(
            dateText = formatFileBrowserDate(resolveDisplayDate(file, metadata)),
            sizeText = formatFileSizeHelper(file.size),
        )
    } else {
        null
    }
    var posterFailureCount by remember(
        file.name,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = if (isVideoFile) {
        selectVideoArtwork(
            metadataState = metadataState,
            posterFailureCount = posterFailureCount,
            videoPath = artworkVideoPath,
        )
    } else {
        VideoArtworkSelection.Placeholder
    }
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    val shouldUsePoster = artworkSelection is VideoArtworkSelection.Poster
    val shouldUseGeneratedFrame = artworkSelection is VideoArtworkSelection.GeneratedFrame
    val generatedThumbnailModel = remember(artworkVideoPath) {
        VideoThumbnailFetcher.Model(
            path = artworkVideoPath,
        )
    }
    val generatedThumbnailDiskCacheKey = remember(artworkVideoPath) {
        VideoThumbnailFetcher.diskCacheKey(
            path = artworkVideoPath,
        )
    }
    val previewSpec = remember(
        file.path,
        isVideoFile,
        artworkSelection,
        posterUrl,
        posterCacheKey,
        previewVideoPath,
    ) {
        if (!isVideoFile || artworkSelection is VideoArtworkSelection.Placeholder) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = artworkVideoPath,
                previewVideoPath = previewVideoPath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        subtitleText,
        previewSpec,
        metadata,
        shouldUsePoster,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "file:${file.path}",
            title = displayTitle,
            subtitle = subtitleText,
            previewSpec = previewSpec,
            fallbackPreviewSpec = if (shouldUsePoster) {
                buildGroupHoverPreviewSpec(
                    shouldUsePoster = false,
                    posterUrl = null,
                    posterCacheKey = null,
                    representativePath = artworkVideoPath,
                    previewVideoPath = previewVideoPath,
                )
            } else {
                null
            },
            metadata = metadata,
            metadataLookupRequest = null,
            onPreviewLoadError = if (shouldUsePoster) {
                { posterFailureCount += 1 }
            } else {
                null
            },
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key
    LaunchedEffect(isPreviewFocused, previewSpec, metadata) {
        if (isPreviewFocused) onPreviewFocused(previewItem)
    }

    var popupAnchorBounds by remember(file.path) { mutableStateOf<IntRect?>(null) }

    FancyFileCard(
        fileName = displayTitle,
        isDirectory = file.isDirectory,
        isVideoFile = isVideoFile,
        supportingText = detailText,
        isFavorite = isFavorite,
        videoPath = if (isVideoFile) file.path else null,
        thumbnailModel = when {
            shouldUseFolderImage -> folderImageUrl
            shouldUseFolderGeneratedFrame -> folderGeneratedThumbnailModel
            shouldUsePoster -> posterUrl
            shouldUseGeneratedFrame -> generatedThumbnailModel
            else -> null
        },
        thumbnailDiskCacheKey = when {
            folderArtworkSelection is FolderArtworkSelection.Poster -> folderPosterCacheKey
            shouldUseFolderGeneratedFrame -> folderGeneratedThumbnailDiskCacheKey
            shouldUsePoster -> posterCacheKey
            shouldUseGeneratedFrame -> generatedThumbnailDiskCacheKey
            else -> null
        },
        thumbnailDiskCachePolicy = if (
            folderArtworkSelection is FolderArtworkSelection.ActorImage
        ) {
            CachePolicy.DISABLED
        } else {
            CachePolicy.ENABLED
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
            if (shouldUseFolderImage) {
                folderArtworkFailureCount += 1
            } else if (shouldUseFolderGeneratedFrame) {
                Log.e(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Generated folder thumbnail failed for folder=${file.path}, path=$folderRepresentativePath, reason=${throwable?.message}",
                )
            } else if (shouldUsePoster) {
                Log.w(
                    FILE_THUMBNAIL_LOG_TAG,
                    "Poster load failed for file=${file.path}, url=$posterUrl, reason=${throwable?.message}. Falling back to generated thumbnail."
                )
                posterFailureCount += 1
            } else if (shouldUseGeneratedFrame) {
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
    folderArtworkKind: BrowserFolderArtworkKind,
    preferredMetadataBaseName: String?,
    metadataRefreshToken: Long,
    viewModel: MainDashboardViewModel,
    sharedMetadataState: ArtworkState<JvrMovieMetadata>?,
    sharedArtworkVideoPath: String?,
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
    val usesSharedMetadata = sharedMetadataState != null
    val artworkVideoPath = sharedArtworkVideoPath ?: file.path
    val previewVideoPath = file.trailerPath ?: artworkVideoPath
    val metadataState = if (sharedMetadataState != null) {
        sharedMetadataState
    } else {
        rememberVideoFileMetadata(
            viewModel = viewModel,
            file = file,
            isVideoFile = isVideoFile,
            preferredBaseName = preferredMetadataBaseName,
            refreshToken = metadataRefreshToken,
        )
    }
    val metadata = metadataState.resolvedValueOrNull()
    val folderArtworkState = rememberBrowserFolderArtwork(
        viewModel = viewModel,
        folder = file,
        kind = folderArtworkKind,
        refreshToken = metadataRefreshToken,
    )
    val folderResolution = folderArtworkState.resolvedValueOrNull()
    var folderArtworkFailureCount by remember(
        file.path,
        folderResolution?.metadata?.posterUrl,
        folderResolution?.metadata?.posterFallbackUrls,
        folderResolution?.actorArtworkUrl,
    ) { mutableStateOf(0) }
    val folderArtworkSelection = selectFolderArtwork(
        artworkState = folderArtworkState,
        imageFailureCount = folderArtworkFailureCount,
    )
    val folderImageUrl = when (folderArtworkSelection) {
        is FolderArtworkSelection.Poster -> folderArtworkSelection.url
        is FolderArtworkSelection.ActorImage -> folderArtworkSelection.url
        else -> null
    }
    val folderRepresentativePath = folderResolution?.representativeVideo?.path
    val shouldUseFolderGeneratedFrame =
        folderArtworkSelection is FolderArtworkSelection.GeneratedFrame
    val folderGeneratedThumbnailModel = folderRepresentativePath?.let { path ->
        VideoThumbnailFetcher.Model(path = path)
    }
    val folderGeneratedThumbnailDiskCacheKey = folderRepresentativePath?.let { path ->
        VideoThumbnailFetcher.diskCacheKey(path = path)
    }
    val folderImageDiskCacheKey = when (folderArtworkSelection) {
        is FolderArtworkSelection.Poster -> {
            buildFilePosterCacheKey(file.name, folderArtworkSelection.url)
        }

        else -> null
    }
    val displayTitle =
        if (!usesSharedMetadata && isVideoFile) metadata?.title ?: file.name else file.name
    val subtitleText = if (!usesSharedMetadata) metadata?.code ?: file.name else file.name
    val detailText = if (!file.isDirectory) {
        buildFileBrowserDetailText(
            dateText = formatFileBrowserDate(resolveDisplayDate(file, metadata)),
            sizeText = formatFileSizeHelper(file.size),
        )
    } else {
        null
    }
    var posterFailureCount by remember(
        file.name,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = if (isVideoFile) {
        selectVideoArtwork(
            metadataState = metadataState,
            posterFailureCount = posterFailureCount,
            videoPath = artworkVideoPath,
        )
    } else {
        VideoArtworkSelection.Placeholder
    }
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(file.name, it) }
    val shouldUsePoster = artworkSelection is VideoArtworkSelection.Poster
    val shouldUseGeneratedFrame = artworkSelection is VideoArtworkSelection.GeneratedFrame
    val generatedThumbnailModel = remember(artworkVideoPath) {
        VideoThumbnailFetcher.Model(
            path = artworkVideoPath,
        )
    }
    val generatedThumbnailDiskCacheKey = remember(artworkVideoPath) {
        VideoThumbnailFetcher.diskCacheKey(
            path = artworkVideoPath,
        )
    }
    val previewSpec = remember(
        file.path,
        isVideoFile,
        artworkSelection,
        posterUrl,
        posterCacheKey,
        previewVideoPath,
    ) {
        if (!isVideoFile || artworkSelection is VideoArtworkSelection.Placeholder) {
            null
        } else {
            buildGroupHoverPreviewSpec(
                shouldUsePoster = shouldUsePoster,
                posterUrl = posterUrl,
                posterCacheKey = posterCacheKey,
                representativePath = artworkVideoPath,
                previewVideoPath = previewVideoPath,
            )
        }
    }
    val previewItem = remember(
        displayTitle,
        subtitleText,
        previewSpec,
        metadata,
        shouldUsePoster,
        onPopupClick,
    ) {
        DashboardPreviewItem(
            key = "file:${file.path}",
            title = displayTitle,
            subtitle = subtitleText,
            previewSpec = previewSpec,
            fallbackPreviewSpec = if (shouldUsePoster) {
                buildGroupHoverPreviewSpec(
                    shouldUsePoster = false,
                    posterUrl = null,
                    posterCacheKey = null,
                    representativePath = artworkVideoPath,
                    previewVideoPath = previewVideoPath,
                )
            } else {
                null
            },
            metadata = metadata,
            metadataLookupRequest = null,
            onPreviewLoadError = if (shouldUsePoster) {
                { posterFailureCount += 1 }
            } else {
                null
            },
            onOpen = onPopupClick,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key
    LaunchedEffect(isPreviewFocused, previewSpec, metadata) {
        if (isPreviewFocused) onPreviewFocused(previewItem)
    }

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
                if (isVideoFile && artworkSelection !is VideoArtworkSelection.Placeholder) {
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
                                    posterFailureCount += 1
                                } else if (shouldUseGeneratedFrame) {
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
                            stringResource(R.string.poster_thumbnail)
                        } else {
                            stringResource(R.string.video_thumbnail)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (
                    folderImageUrl != null ||
                    (shouldUseFolderGeneratedFrame && folderGeneratedThumbnailModel != null)
                ) {
                    val folderModel = folderImageUrl ?: folderGeneratedThumbnailModel
                    val folderDiskCacheKey = if (folderImageUrl != null) {
                        folderImageDiskCacheKey
                    } else {
                        folderGeneratedThumbnailDiskCacheKey
                    }
                    val folderRequestBuilder = ImageRequest.Builder(context)
                        .data(folderModel)
                        .diskCachePolicy(
                            if (folderArtworkSelection is FolderArtworkSelection.ActorImage) {
                                CachePolicy.DISABLED
                            } else {
                                CachePolicy.ENABLED
                            }
                        )
                        .memoryCachePolicy(CachePolicy.ENABLED)
                    if (folderDiskCacheKey != null) {
                        folderRequestBuilder.diskCacheKey(folderDiskCacheKey)
                    }
                    AsyncImage(
                        model = folderRequestBuilder.build(),
                        imageLoader = ThumbnailImageLoaderProvider.get(context),
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            if (
                                state is coil3.compose.AsyncImagePainter.State.Error &&
                                folderImageUrl != null
                            ) {
                                folderArtworkFailureCount += 1
                            }
                        },
                    )
                } else {
                    Icon(
                        imageVector = if (isVideoFile) {
                            Icons.Filled.Movie
                        } else {
                            Icons.Filled.FolderOpen
                        },
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
                                stringResource(R.string.remove_from_favorites)
                            } else {
                                stringResource(R.string.add_to_favorites)
                            },
                            tint = if (isFavorite) NetflixRed else TextPrimary
                        )
                    }
                }

                if (isSelectionMode && isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.selected_for_deletion),
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

            detailText?.let { supportingText ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = supportingText,
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

@Composable
private fun LibraryDestinationPanel(
    favorites: List<FavoriteVideo>,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    dashboardContentWidth: Dp,
    previewItem: DashboardPreviewItem?,
    onFavoriteClick: (FavoriteVideo) -> Unit,
    onFavoriteToggle: (String, String, String, String, Boolean) -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var previewWidthOverrideDp by rememberSaveable { mutableStateOf<Float?>(null) }
    val defaultPreviewWidth = when {
        dashboardContentWidth >= 1500.dp -> 420.dp
        dashboardContentWidth >= 1180.dp -> 360.dp
        else -> 300.dp
    }
    val minPreviewWidth = 280.dp
    val minPrimaryPaneWidth = 460.dp
    val dividerWidth = 18.dp
    val maxPreviewWidth = (dashboardContentWidth - dividerWidth - minPrimaryPaneWidth)
        .coerceAtLeast(minPreviewWidth)

    LaunchedEffect(defaultPreviewWidth, minPreviewWidth, maxPreviewWidth) {
        val currentWidth = previewWidthOverrideDp ?: defaultPreviewWidth.value
        val clampedWidth = currentWidth.coerceIn(
            minPreviewWidth.value,
            maxPreviewWidth.value,
        )
        if (previewWidthOverrideDp != clampedWidth) {
            previewWidthOverrideDp = clampedWidth
        }
    }

    val previewWidth = (previewWidthOverrideDp ?: defaultPreviewWidth.value)
        .coerceIn(minPreviewWidth.value, maxPreviewWidth.value)
        .dp

    Row(modifier = modifier) {
        FavoritesPanel(
            favorites = favorites,
            viewModel = viewModel,
            metadataRefreshToken = metadataRefreshToken,
            isConnected = true,
            currentPreviewKey = previewItem?.key,
            onFavoriteClick = onFavoriteClick,
            onFavoriteToggle = onFavoriteToggle,
            onPreviewFocused = onPreviewFocused,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )

        ConnectedPaneResizeHandle(
            onDragDeltaPx = { deltaPx ->
                val deltaDp = with(density) { deltaPx.toDp().value }
                val currentWidth = previewWidthOverrideDp ?: defaultPreviewWidth.value
                previewWidthOverrideDp = (currentWidth - deltaDp).coerceIn(
                    minPreviewWidth.value,
                    maxPreviewWidth.value,
                )
            },
            modifier = Modifier.fillMaxHeight(),
        )

        DashboardPreviewPanel(
            previewItem = previewItem,
            viewModel = viewModel,
            modifier = Modifier
                .width(previewWidth)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun FavoritesPanel(
    favorites: List<FavoriteVideo>,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    isConnected: Boolean,
    currentPreviewKey: String?,
    onFavoriteClick: (FavoriteVideo) -> Unit,
    onFavoriteToggle: (String, String, String, String, Boolean) -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(isConnected, favorites) {
        if (!isConnected || favorites.isEmpty()) {
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
                    text = stringResource(R.string.library),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = NetflixRed,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                !isConnected -> {
                    EmptyState(
                        icon = Icons.Filled.Cloud,
                        message = stringResource(R.string.connect_to_view_library),
                    )
                }

                favorites.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.FavoriteBorder,
                        message = stringResource(R.string.no_favorites_yet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(favorites) { video ->
                            LibraryMovieEntry(
                                video = video,
                                isFavorite = true,
                                viewModel = viewModel,
                                metadataRefreshToken = metadataRefreshToken,
                                currentPreviewKey = currentPreviewKey,
                                onOpen = { onFavoriteClick(video) },
                                onFavoriteToggle = {
                                    onFavoriteToggle(
                                        video.filePath,
                                        video.fileName,
                                        video.serverAddress,
                                        video.shareName,
                                        true,
                                    )
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

@Composable
private fun LibraryMovieEntry(
    video: LibraryVideoItem,
    isFavorite: Boolean,
    viewModel: MainDashboardViewModel,
    metadataRefreshToken: Long,
    currentPreviewKey: String?,
    onOpen: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPreviewFocused: (DashboardPreviewItem?) -> Unit,
) {
    val context = LocalContext.current
    val file = remember(video.filePath, video.fileName) {
        SMBFileItem(
            name = video.fileName,
            path = video.filePath,
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
        )
    }
    val metadataState = rememberVideoFileMetadata(
        viewModel = viewModel,
        file = file,
        isVideoFile = true,
        refreshToken = metadataRefreshToken,
    )
    val metadata = metadataState.resolvedValueOrNull()
    var posterFailureCount by remember(
        video.filePath,
        metadata?.posterUrl,
        metadata?.posterFallbackUrls,
    ) { mutableStateOf(0) }
    val artworkSelection = selectVideoArtwork(
        metadataState = metadataState,
        posterFailureCount = posterFailureCount,
        videoPath = video.filePath,
    )
    val posterUrl = (artworkSelection as? VideoArtworkSelection.Poster)?.url
    val validGeneratedFramePath = if (
        artworkSelection is VideoArtworkSelection.GeneratedFrame &&
        VideoThumbnailFetcher.isCurrentGeneratedFramePath(
            context = context,
            videoPath = video.filePath,
            thumbnailPath = video.thumbnailPath,
        )
    ) {
        video.thumbnailPath
    } else {
        null
    }
    val posterCacheKey = posterUrl?.let { buildFilePosterCacheKey(video.fileName, it) }
    val thumbnailModel = when (artworkSelection) {
        is VideoArtworkSelection.Poster -> artworkSelection.url
        is VideoArtworkSelection.GeneratedFrame -> {
            validGeneratedFramePath ?: VideoThumbnailFetcher.Model(artworkSelection.videoPath)
        }

        VideoArtworkSelection.Placeholder -> null
    }
    val thumbnailDiskCacheKey = when (artworkSelection) {
        is VideoArtworkSelection.Poster -> posterCacheKey
        is VideoArtworkSelection.GeneratedFrame -> {
            validGeneratedFramePath
                ?: VideoThumbnailFetcher.diskCacheKey(artworkSelection.videoPath)
        }

        VideoArtworkSelection.Placeholder -> null
    }
    val displayTitle = metadata?.title?.takeIf { it.isNotBlank() }
        ?: video.resolvedTitle?.takeIf { it.isNotBlank() }
        ?: video.fileName
    val previewSpec = when (artworkSelection) {
        is VideoArtworkSelection.Poster -> GroupHoverPreviewSpec(
            model = artworkSelection.url,
            diskCacheKey = posterCacheKey,
            source = "poster",
            extraFanartLookupPath = video.filePath,
            fallbackVideoPath = video.filePath,
        )

        is VideoArtworkSelection.GeneratedFrame -> buildLibraryPreviewSpec(
            video = video,
            generatedFramePath = validGeneratedFramePath,
        )

        VideoArtworkSelection.Placeholder -> null
    }
    val previewItem = remember(
        video.filePath,
        video.fileName,
        displayTitle,
        previewSpec,
        metadata,
        artworkSelection,
        validGeneratedFramePath,
        onOpen,
    ) {
        DashboardPreviewItem(
            key = "library:${video.filePath}",
            title = displayTitle,
            subtitle = video.fileName,
            previewSpec = previewSpec,
            fallbackPreviewSpec = if (artworkSelection is VideoArtworkSelection.Poster) {
                buildLibraryPreviewSpec(
                    video = video,
                    generatedFramePath = validGeneratedFramePath,
                )
            } else {
                null
            },
            metadata = metadata,
            metadataLookupRequest = null,
            onPreviewLoadError = if (artworkSelection is VideoArtworkSelection.Poster) {
                { posterFailureCount += 1 }
            } else {
                null
            },
            onOpen = onOpen,
        )
    }
    val isPreviewFocused = currentPreviewKey == previewItem.key
    LaunchedEffect(isPreviewFocused, previewSpec, metadata, posterFailureCount) {
        if (isPreviewFocused) onPreviewFocused(previewItem)
    }

    FancyMovieCard(
        video = video,
        isFavorite = isFavorite,
        displayTitleOverride = displayTitle,
        thumbnailModel = thumbnailModel,
        thumbnailDiskCacheKey = thumbnailDiskCacheKey,
        onThumbnailLoadError = if (artworkSelection is VideoArtworkSelection.Poster) {
            { posterFailureCount += 1 }
        } else {
            null
        },
        onClick = onOpen,
        onFavoriteToggle = onFavoriteToggle,
        isPreviewFocused = isPreviewFocused,
        onRequestPreview = { onPreviewFocused(previewItem) },
        onHoverFocusChanged = { isFocused ->
            if (isFocused) {
                onPreviewFocused(previewItem)
            } else if (currentPreviewKey == previewItem.key) {
                onPreviewFocused(null)
            }
        },
    )
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
    initialCredentials: SmbCredentials = SmbCredentials(),
    onDismiss: () -> Unit,
    onSave: (SavedServer, SmbCredentials) -> Unit,
    onTestConnection: suspend (SavedServer, SmbCredentials) -> Result<Unit>
) {
    val connectionFailedPrefix = stringResource(R.string.connection_failed_prefix)
    val invalidPortMessage = stringResource(R.string.invalid_smb_port)
    var name by remember(initialServer?.id) { mutableStateOf(initialServer?.serverName ?: "") }
    var address by remember(initialServer?.id) {
        mutableStateOf(initialServer?.serverAddress.orEmpty())
    }
    var shareName by remember(initialServer?.id) { mutableStateOf(initialServer?.shareName ?: "") }
    var port by remember(initialServer?.id) {
        mutableStateOf(initialServer?.port?.toString().orEmpty())
    }
    var username by remember(initialServer?.id) { mutableStateOf(initialCredentials.username) }
    var password by remember(initialServer?.id) { mutableStateOf(initialCredentials.password) }
    var domain by remember(initialServer?.id) { mutableStateOf(initialCredentials.domain) }
    var showAdvanced by remember(initialServer?.id) { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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
                    text = if (initialServer == null) {
                        stringResource(R.string.add_server)
                    } else {
                        stringResource(R.string.edit_server_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.smb_address)) },
                        placeholder = { Text(stringResource(R.string.smb_address_example)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.user_id_optional)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTestingConnection
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        enabled = !isTestingConnection
                    )
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        enabled = !isTestingConnection,
                    ) {
                        Text(
                            stringResource(
                                if (showAdvanced) R.string.hide_advanced_options
                                else R.string.show_advanced_options
                            )
                        )
                    }
                    if (showAdvanced) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.server_name_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isTestingConnection,
                        )
                        OutlinedTextField(
                            value = shareName,
                            onValueChange = { shareName = it },
                            label = { Text(stringResource(R.string.share_name_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isTestingConnection,
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { value -> port = value.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.port_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isTestingConnection,
                        )
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text(stringResource(R.string.domain_optional)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isTestingConnection,
                        )
                    }
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
                        Text(stringResource(R.string.cancel), color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val isSaveEnabled = address.isNotBlank() && !isTestingConnection

                    Button(
                        onClick = {
                            if (isSaveEnabled) {
                                scope.launch {
                                    isTestingConnection = true
                                    connectionError = null

                                    val endpointResult = SmbEndpointParser.parse(address)
                                    val endpoint = endpointResult.getOrElse { error ->
                                        connectionError = error.message
                                        isTestingConnection = false
                                        return@launch
                                    }
                                    val advancedPort = port.toIntOrNull()
                                    if (advancedPort != null && advancedPort !in 1..65535) {
                                        connectionError = invalidPortMessage
                                        isTestingConnection = false
                                        return@launch
                                    }
                                    val credentials = SmbCredentials(
                                        username = username.trim(),
                                        password = password,
                                        domain = domain.trim(),
                                    )
                                    val serverToSave = SavedServer(
                                        id = initialServer?.id ?: 0,
                                        serverName = name.trim().ifBlank { endpoint.suggestedName },
                                        serverAddress = endpoint.address,
                                        port = advancedPort ?: endpoint.port,
                                        shareName = shareName.trim().ifBlank { endpoint.shareName },
                                        credentialAlias = initialServer?.credentialAlias
                                            ?.takeIf(String::isNotBlank)
                                            ?: SmbCredentialStore.aliasForNewServer(),
                                    )

                                    val result = onTestConnection(serverToSave, credentials)

                                    if (result.isSuccess) {
                                        onSave(serverToSave, credentials)
                                    } else {
                                        connectionError = buildString {
                                            append(connectionFailedPrefix)
                                            val detail =
                                                result.exceptionOrNull()?.message.orEmpty()
                                            if (detail.isNotBlank()) {
                                                append(' ')
                                                append(detail)
                                            }
                                        }
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
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}

private fun buildGroupPosterCacheKey(groupKey: String, posterUrl: String): String {
    val generation = ImageCacheVersionStore.artworkGeneration()
    return "group-poster:$generation:$groupKey:${posterUrl.hashCode()}"
}

private fun buildFilePosterCacheKey(fileName: String, posterUrl: String): String {
    val generation = ImageCacheVersionStore.artworkGeneration()
    return "file-poster:$generation:${fileName.hashCode()}:${posterUrl.hashCode()}"
}

private val fileBrowserZoneId: ZoneId = ZoneId.systemDefault()

private fun buildVirtualVideoGroups(files: List<SMBFileItem>): Map<String, VirtualVideoGroup> {
    return groupMultipartVideoFiles(files).mapValues { (groupKey, groupedFiles) ->
        VirtualVideoGroup(
            key = groupKey,
            files = groupedFiles,
            representativeFile = groupedFiles.firstOrNull(),
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

private fun resolveDisplayDate(file: SMBFileItem, metadata: JvrMovieMetadata?): LocalDate? {
    return metadata?.releaseDate ?: lastModifiedToLocalDate(file.lastModified)
}

private fun resolveDisplayDate(group: VirtualVideoGroup, metadata: JvrMovieMetadata?): LocalDate? {
    return metadata?.releaseDate
        ?: group.representativeFile?.lastModified?.let(::lastModifiedToLocalDate)
}

private fun lastModifiedToLocalDate(lastModified: Long): LocalDate? {
    if (lastModified <= 0L) {
        return null
    }

    return Instant.ofEpochMilli(lastModified)
        .atZone(fileBrowserZoneId)
        .toLocalDate()
}

private fun formatFileBrowserDate(date: LocalDate?): String? {
    return date?.toString()
}

private fun buildFileBrowserDetailText(dateText: String?, sizeText: String?): String? {
    val values = listOfNotNull(dateText, sizeText)
    return values.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
