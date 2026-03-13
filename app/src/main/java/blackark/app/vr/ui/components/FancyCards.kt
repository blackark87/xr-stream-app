package blackark.app.vr.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import blackark.app.vr.data.database.entity.RecentVideo
import blackark.app.vr.data.database.entity.SavedServer
import blackark.app.vr.ui.theme.AccentGold
import blackark.app.vr.ui.theme.CardBackground
import blackark.app.vr.ui.theme.CardBackgroundHover
import blackark.app.vr.ui.theme.DividerGray
import blackark.app.vr.ui.theme.NetflixDarkRed
import blackark.app.vr.ui.theme.NetflixRed
import blackark.app.vr.ui.theme.StreamingBlack
import blackark.app.vr.ui.theme.SuccessGreen
import blackark.app.vr.ui.theme.TextPrimary
import blackark.app.vr.ui.theme.TextSecondary
import blackark.app.vr.ui.theme.TextTertiary
import blackark.app.vr.utils.ThumbnailImageLoaderProvider
import blackark.app.vr.utils.VideoThumbnailFetcher

/**
 * Local Storage Card - Special card for device storage
 */
@Composable
fun LocalStorageCard(
    onClick: () -> Unit,
    isSelected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 12.dp else 4.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                enabled = !isConnecting
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBackgroundHover else CardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected || isHovered || isFocused) BorderStroke(2.dp, AccentGold) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Storage Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(AccentGold, AccentGold.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Home, // Changed from Phone to Home
                    contentDescription = "Local Storage",
                    tint = StreamingBlack,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Local Storage",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device internal storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Selected indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = SuccessGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Fancy Server Card - Material-style elevated card
 */
@Composable
fun FancyServerCard(
    server: SavedServer,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 12.dp else 4.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "scale"
    )

    val outlineColor by animateColorAsState(
        targetValue = if (isHovered || isFocused) {
            NetflixRed.copy(alpha = 0.92f)
        } else {
            DividerGray.copy(alpha = 0.95f)
        },
        animationSpec = tween(220),
        label = "serverOutlineColor"
    )

    val outlineWidth by animateDpAsState(
        targetValue = if (isHovered || isFocused) 2.dp else 1.dp,
        animationSpec = tween(220),
        label = "serverOutlineWidth"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(outlineWidth, outlineColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp), // Reduced padding from 16.dp
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Server Icon
            Box(
                modifier = Modifier
                    .size(48.dp) // Reduced size from 56.dp
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(NetflixRed, NetflixDarkRed)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = "Server",
                    tint = TextPrimary,
                    modifier = Modifier.size(28.dp) // Reduced size from 32.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp)) // Reduced spacing from 16.dp

            // Server Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = server.serverName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2, // Increased maxLines
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Edit Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit Server",
                    tint = TextSecondary
                )
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete Server",
                    tint = TextSecondary
                )
            }
        }
    }
}

/**
 * Fancy Movie Card - Material-style media card (without thumbnail)
 */
@Composable
fun FancyMovieCard(
    video: RecentVideo,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onHoverFocusChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isHovered, isFocused, onHoverFocusChanged) {
        onHoverFocusChanged?.invoke(isHovered || isFocused)
    }

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 12.dp else 4.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isHovered || isFocused) BorderStroke(2.dp, NetflixRed) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Movie Icon or Thumbnail
                Box(
                    modifier = Modifier
                        .size(80.dp, 60.dp) // Adjusted aspect ratio for video thumbnail
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (video.filePath.isNotEmpty()) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(VideoThumbnailFetcher.Model(video.filePath))
                                .diskCacheKey(video.filePath)
                                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .build(),
                            imageLoader = ThumbnailImageLoaderProvider.get(androidx.compose.ui.platform.LocalContext.current),
                            contentDescription = "Video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Filled.Movie)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = "Movie",
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                val displayTitle = video.resolvedTitle?.takeIf { it.isNotBlank() }
                    ?: video.fileName.removeSuffix(".mp4")
                        .removeSuffix(".mkv")
                        .removeSuffix(".avi")
                        .removeSuffix(".mov")

                // Movie Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress indicator if video was partially watched
                    if (video.duration > 0 && video.lastPosition > 0) {
                        val progress =
                            (video.lastPosition.toFloat() / video.duration.toFloat()).coerceIn(
                                0f,
                                1f
                            )
                        Column {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NetflixRed,
                                trackColor = DividerGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${(progress * 100).toInt()}% watched",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }

                // Favorite Button
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) NetflixRed else TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Fancy File Item Card - For file browser
 */
@Composable
fun FancyFileCard(
    fileName: String,
    isDirectory: Boolean,
    isVideoFile: Boolean,
    fileSize: String? = null,
    isFavorite: Boolean = false,
    videoPath: String? = null,
    thumbnailModel: Any? = null,
    thumbnailDiskCacheKey: String? = null,
    onThumbnailLoadSuccess: (() -> Unit)? = null,
    onThumbnailLoadError: ((Throwable?) -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()


    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) CardBackgroundHover else CardBackground,
        animationSpec = tween(200),
        label = "background"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected || isHovered || isFocused) BorderStroke(
            2.dp,
            if (isSelected) AccentGold else NetflixRed
        ) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon or Thumbnail
            Box(
                modifier = Modifier
                    .size(if (isVideoFile) 80.dp else 32.dp, if (isVideoFile) 60.dp else 32.dp)
                    .clip(RoundedCornerShape(if (isVideoFile) 8.dp else 0.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isVideoFile && videoPath != null) {
                    // Show thumbnail for video files with fallback icon
                    android.util.Log.d("FancyFileCard", "Rendering thumbnail for: $videoPath")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DividerGray),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(thumbnailModel ?: VideoThumbnailFetcher.Model(videoPath))
                                .diskCacheKey(thumbnailDiskCacheKey ?: videoPath)
                                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .build(),
                            imageLoader = ThumbnailImageLoaderProvider.get(androidx.compose.ui.platform.LocalContext.current),
                            contentDescription = "Video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                android.util.Log.d("FancyFileCard", "AsyncImage state: $state")
                                if (state is coil3.compose.AsyncImagePainter.State.Success) {
                                    onThumbnailLoadSuccess?.invoke()
                                }
                                if (state is coil3.compose.AsyncImagePainter.State.Error) {
                                    android.util.Log.e(
                                        "FancyFileCard",
                                        "AsyncImage Error: ${state.result.throwable.message}",
                                        state.result.throwable
                                    )
                                    onThumbnailLoadError?.invoke(state.result.throwable)
                                }
                            }
                        )
                    }
                } else {
                    // Regular icon for directories and non-video files
                    Icon(
                        imageVector = when {
                            isDirectory -> Icons.Filled.Folder
                            isVideoFile -> Icons.Filled.Movie
                            else -> Icons.Filled.Description
                        },
                        contentDescription = null,
                        tint = when {
                            isDirectory -> AccentGold
                            isVideoFile -> NetflixRed
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isVideoFile) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (fileSize != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = fileSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            // Favorite button for video files
            if (isVideoFile && onFavoriteToggle != null) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) NetflixRed else TextSecondary
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected for deletion",
                    tint = AccentGold,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 6.dp)
                )
            }

            // Arrow for directories
            if (isDirectory) {
                Text(
                    text = ">",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Empty State Component
 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextTertiary
        )
    }
}

