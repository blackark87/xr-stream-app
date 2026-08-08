package blackark.app.vr.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

data class RuntimeConfig(
    val features: FeatureRuntimeConfig = FeatureRuntimeConfig(),
    val defaults: UserDefaultRuntimeConfig = UserDefaultRuntimeConfig(),
    val preview: PreviewRuntimeConfig = PreviewRuntimeConfig(),
    val playback: PlaybackRuntimeConfig = PlaybackRuntimeConfig(),
    val controller: ControllerRuntimeConfig = ControllerRuntimeConfig(),
    val headFollow: HeadFollowRuntimeConfig = HeadFollowRuntimeConfig(),
    val seekPreview: SeekPreviewRuntimeConfig = SeekPreviewRuntimeConfig(),
    val subtitles: SubtitleRuntimeConfig = SubtitleRuntimeConfig(),
    val metadata: MetadataRuntimeConfig = MetadataRuntimeConfig(),
    val cache: CacheRuntimeConfig = CacheRuntimeConfig(),
) {
    fun validated(): RuntimeConfig {
        val bundled = RuntimeConfig()
        return copy(
            preview = preview.copy(
                focusDelayMs = preview.focusDelayMs.coerceIn(0L, 5_000L),
                mainVideoDurationMs = preview.mainVideoDurationMs.coerceIn(5_000L, 300_000L),
                fanartIntervalMs = preview.fanartIntervalMs.coerceIn(1_000L, 30_000L),
                defaultVolume = preview.defaultVolume.coerceFiniteIn(
                    0f,
                    1f,
                    bundled.preview.defaultVolume,
                ),
            ),
            playback = playback.copy(
                controlsAutoHideMs = playback.controlsAutoHideMs.coerceIn(2_000L, 60_000L),
                seekStepMs = playback.seekStepMs.coerceIn(1_000L, 60_000L),
                seekInitialRepeatMs = playback.seekInitialRepeatMs.coerceIn(100L, 2_000L),
                seekRepeatMs = playback.seekRepeatMs.coerceIn(50L, 1_000L),
                volumeStep = playback.volumeStep.coerceFiniteIn(
                    0.01f,
                    0.25f,
                    bundled.playback.volumeStep,
                ),
                volumeInitialRepeatMs = playback.volumeInitialRepeatMs.coerceIn(100L, 2_000L),
                volumeRepeatMs = playback.volumeRepeatMs.coerceIn(50L, 1_000L),
            ),
            controller = controller.copy(
                axisEmitThreshold = controller.axisEmitThreshold.coerceFiniteIn(
                    0.01f, 0.5f, bundled.controller.axisEmitThreshold,
                ),
                axisProfileThreshold = controller.axisProfileThreshold.coerceFiniteIn(
                    0.01f, 0.5f, bundled.controller.axisProfileThreshold,
                ),
                deadZone = controller.deadZone.coerceFiniteIn(
                    0.01f, 0.5f, bundled.controller.deadZone,
                ),
                engageThreshold = controller.engageThreshold.coerceFiniteIn(
                    0.1f, 0.95f, bundled.controller.engageThreshold,
                ),
                releaseThreshold = controller.releaseThreshold.coerceFiniteIn(
                    0.05f, 0.9f, bundled.controller.releaseThreshold,
                ),
                dominanceMargin = controller.dominanceMargin.coerceFiniteIn(
                    0f, 0.5f, bundled.controller.dominanceMargin,
                ),
                logIntervalMs = controller.logIntervalMs.coerceIn(100L, 5_000L),
            ).let { value ->
                if (value.releaseThreshold >= value.engageThreshold) {
                    value.copy(releaseThreshold = (value.engageThreshold - 0.05f).coerceAtLeast(0.05f))
                } else {
                    value
                }
            },
            headFollow = headFollow.copy(
                video180DeadZoneDegrees = headFollow.video180DeadZoneDegrees.coerceFiniteIn(
                    5f, 90f, bundled.headFollow.video180DeadZoneDegrees,
                ),
                controls360DeadZoneDegrees = headFollow.controls360DeadZoneDegrees.coerceFiniteIn(
                    3f, 60f, bundled.headFollow.controls360DeadZoneDegrees,
                ),
                video180Smoothing = headFollow.video180Smoothing.coerceFiniteIn(
                    0.05f, 1f, bundled.headFollow.video180Smoothing,
                ),
                controls360Smoothing = headFollow.controls360Smoothing.coerceFiniteIn(
                    0.05f, 1f, bundled.headFollow.controls360Smoothing,
                ),
                controlsDistanceDp = headFollow.controlsDistanceDp.coerceFiniteIn(
                    400f, 2_000f, bundled.headFollow.controlsDistanceDp,
                ),
                backgroundInputDistanceDp = headFollow.backgroundInputDistanceDp
                    .coerceFiniteIn(400f, 2_200f, bundled.headFollow.backgroundInputDistanceDp),
                controlsDownOffsetDp = headFollow.controlsDownOffsetDp.coerceFiniteIn(
                    -800f, 200f, bundled.headFollow.controlsDownOffsetDp,
                ),
                flatPanelVerticalOffsetDp = headFollow.flatPanelVerticalOffsetDp
                    .coerceFiniteIn(-400f, 400f, bundled.headFollow.flatPanelVerticalOffsetDp),
                menuControlsGapDp = headFollow.menuControlsGapDp.coerceFiniteIn(
                    0f, 200f, bundled.headFollow.menuControlsGapDp,
                ),
                menuFrontOffsetDp = headFollow.menuFrontOffsetDp.coerceFiniteIn(
                    0f, 100f, bundled.headFollow.menuFrontOffsetDp,
                ),
                seekPreviewControlsGapDp = headFollow.seekPreviewControlsGapDp.coerceFiniteIn(
                    0f, 200f, bundled.headFollow.seekPreviewControlsGapDp,
                ),
                seekPreviewFrontOffsetDp = headFollow.seekPreviewFrontOffsetDp.coerceFiniteIn(
                    0f, 100f, bundled.headFollow.seekPreviewFrontOffsetDp,
                ),
                subtitleBaseUpOffsetDp = headFollow.subtitleBaseUpOffsetDp.coerceFiniteIn(
                    -200f, 800f, bundled.headFollow.subtitleBaseUpOffsetDp,
                ),
            ),
            seekPreview = seekPreview.copy(
                bucketMs = seekPreview.bucketMs.coerceIn(500L, 10_000L),
                memoryEntries = seekPreview.memoryEntries.coerceIn(1, 48),
                frameHeight = seekPreview.frameHeight.coerceIn(120, 720),
                fallbackFrameWidth = seekPreview.fallbackFrameWidth.coerceIn(160, 1_280),
            ),
            subtitles = validateSubtitles(subtitles),
            metadata = metadata.copy(
                baseUrl = validateHttpsUrl(
                    candidate = metadata.baseUrl,
                    fallback = bundled.metadata.baseUrl,
                    allowedHosts = setOf("jvrlibrary.com", "www.jvrlibrary.com"),
                ),
                avWikiBaseUrl = validateHttpsUrl(
                    candidate = metadata.avWikiBaseUrl,
                    fallback = bundled.metadata.avWikiBaseUrl,
                    allowedHosts = setOf("av-wiki.net", "www.av-wiki.net"),
                ),
                requestIntervalMs = metadata.requestIntervalMs.coerceIn(0L, 10_000L),
                contentIdPattern = validateRegex(
                    metadata.contentIdPattern,
                    bundled.metadata.contentIdPattern,
                    minimumCaptureGroups = 2,
                ),
                vrTokenPattern = validateRegex(metadata.vrTokenPattern, bundled.metadata.vrTokenPattern),
                videoExtensions = validateExtensions(
                    metadata.videoExtensions,
                    bundled.metadata.videoExtensions,
                    allowed = null,
                ),
                imageExtensions = validateExtensions(
                    metadata.imageExtensions,
                    bundled.metadata.imageExtensions,
                    allowed = SUPPORTED_IMAGE_EXTENSIONS,
                ),
                artworkPriority = metadata.artworkPriority
                    .map(String::trim)
                    .filter { it in SUPPORTED_ARTWORK_PRIORITIES }
                    .distinct()
                    .ifEmpty { bundled.metadata.artworkPriority },
            ),
            cache = cache.copy(
                imageMemoryPercent = cache.imageMemoryPercent.coerceFiniteIn(
                    0.05,
                    0.5,
                    bundled.cache.imageMemoryPercent,
                ),
                imageDiskBytes = cache.imageDiskBytes.coerceIn(64L * MIB, 2L * GIB),
            ),
        )
    }

    companion object {
        private const val MIB = 1024L * 1024L
        private const val GIB = 1024L * MIB
        private val SUPPORTED_ARTWORK_PRIORITIES = setOf(
            "nfoPoster",
            "nfoThumb",
            "sidecarPoster",
            "fanart",
            "cover",
            "folder",
            "generatedFrame",
        )
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val SAFE_EXTENSION_PATTERN = Regex("^[a-z0-9]{1,8}$")

        private fun Float.coerceFiniteIn(
            minimum: Float,
            maximum: Float,
            fallback: Float,
        ): Float = if (isFinite()) coerceIn(minimum, maximum) else fallback

        private fun Double.coerceFiniteIn(
            minimum: Double,
            maximum: Double,
            fallback: Double,
        ): Double = if (isFinite()) coerceIn(minimum, maximum) else fallback

        private fun validateRegex(
            candidate: String,
            fallback: String,
            minimumCaptureGroups: Int = 0,
        ): String = runCatching {
            val regex = Regex(candidate)
            require(regex.toPattern().matcher("").groupCount() >= minimumCaptureGroups)
            candidate
        }.getOrDefault(fallback)

        private fun validateExtensions(
            candidates: List<String>,
            fallback: List<String>,
            allowed: Set<String>?,
        ): List<String> = candidates.asSequence()
            .map { it.trim().trimStart('.').lowercase() }
            .filter(SAFE_EXTENSION_PATTERN::matches)
            .filter { allowed == null || it in allowed }
            .distinct()
            .take(32)
            .toList()
            .ifEmpty { fallback }

        private fun validateSubtitles(candidate: SubtitleRuntimeConfig): SubtitleRuntimeConfig {
            val defaults = SubtitleRuntimeConfig()
            val firstDistanceBound = candidate.minDistanceMeters.coerceFiniteIn(
                0.3f, 3f, defaults.minDistanceMeters,
            )
            val secondDistanceBound = candidate.maxDistanceMeters.coerceFiniteIn(
                0.6f, 5f, defaults.maxDistanceMeters,
            )
            val minDistance = minOf(firstDistanceBound, secondDistanceBound)
            val maxDistance = maxOf(firstDistanceBound, secondDistanceBound)
            val firstVerticalBound = candidate.minVerticalOffsetMeters.coerceFiniteIn(
                -1f, 0f, defaults.minVerticalOffsetMeters,
            )
            val secondVerticalBound = candidate.maxVerticalOffsetMeters.coerceFiniteIn(
                0f, 1f, defaults.maxVerticalOffsetMeters,
            )
            val minVertical = minOf(firstVerticalBound, secondVerticalBound)
            val maxVertical = maxOf(firstVerticalBound, secondVerticalBound)
            val firstHorizontalBound = candidate.minHorizontalOffsetMeters.coerceFiniteIn(
                -1f, 0f, defaults.minHorizontalOffsetMeters,
            )
            val secondHorizontalBound = candidate.maxHorizontalOffsetMeters.coerceFiniteIn(
                0f, 1f, defaults.maxHorizontalOffsetMeters,
            )
            val minHorizontal = minOf(firstHorizontalBound, secondHorizontalBound)
            val maxHorizontal = maxOf(firstHorizontalBound, secondHorizontalBound)
            return candidate.copy(
                defaultDistanceMeters = candidate.defaultDistanceMeters
                    .coerceFiniteIn(0.5f, 4f, defaults.defaultDistanceMeters)
                    .coerceIn(minDistance, maxDistance),
                minDistanceMeters = minDistance,
                maxDistanceMeters = maxDistance,
                defaultVerticalOffsetMeters = candidate.defaultVerticalOffsetMeters
                    .coerceFiniteIn(-0.5f, 0.5f, defaults.defaultVerticalOffsetMeters)
                    .coerceIn(minVertical, maxVertical),
                minVerticalOffsetMeters = minVertical,
                maxVerticalOffsetMeters = maxVertical,
                defaultHorizontalOffsetMeters = candidate.defaultHorizontalOffsetMeters
                    .coerceFiniteIn(-0.5f, 0.5f, defaults.defaultHorizontalOffsetMeters)
                    .coerceIn(minHorizontal, maxHorizontal),
                minHorizontalOffsetMeters = minHorizontal,
                maxHorizontalOffsetMeters = maxHorizontal,
            )
        }

        private fun validateHttpsUrl(
            candidate: String,
            fallback: String,
            allowedHosts: Set<String>,
        ): String {
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return fallback
            return candidate.takeIf {
                uri.scheme.equals("https", ignoreCase = true) &&
                    uri.host?.lowercase() in allowedHosts &&
                    uri.userInfo == null &&
                    (uri.port == -1 || uri.port == 443)
            } ?: fallback
        }
    }
}

data class FeatureRuntimeConfig(
    val motionPreviewEnabled: Boolean = true,
    val generatedSeekPreviewEnabled: Boolean = true,
)

data class UserDefaultRuntimeConfig(
    val backgroundIndexingEnabled: Boolean = false,
    val handTrackingEnabled: Boolean = true,
    val playbackControlsHeadFollowEnabled: Boolean = true,
    val libraryHoverPreviewEnabled: Boolean = true,
    val smbHoverPreviewEnabled: Boolean = true,
)

data class PreviewRuntimeConfig(
    val focusDelayMs: Long = 800L,
    val mainVideoDurationMs: Long = 60_000L,
    val fanartIntervalMs: Long = 4_000L,
    val defaultVolume: Float = 1f,
)

data class PlaybackRuntimeConfig(
    val controlsAutoHideMs: Long = 10_000L,
    val seekStepMs: Long = 7_500L,
    val seekInitialRepeatMs: Long = 260L,
    val seekRepeatMs: Long = 140L,
    val volumeStep: Float = 0.04f,
    val volumeInitialRepeatMs: Long = 220L,
    val volumeRepeatMs: Long = 130L,
)

data class ControllerRuntimeConfig(
    val axisEmitThreshold: Float = 0.08f,
    val axisProfileThreshold: Float = 0.08f,
    val deadZone: Float = 0.08f,
    val engageThreshold: Float = 0.45f,
    val releaseThreshold: Float = 0.25f,
    val dominanceMargin: Float = 0.10f,
    val logIntervalMs: Long = 250L,
)

data class HeadFollowRuntimeConfig(
    val video180DeadZoneDegrees: Float = 30f,
    val controls360DeadZoneDegrees: Float = 12f,
    val video180Smoothing: Float = 0.18f,
    val controls360Smoothing: Float = 0.45f,
    val controlsDistanceDp: Float = 900f,
    val backgroundInputDistanceDp: Float = 980f,
    val controlsDownOffsetDp: Float = -320f,
    val flatPanelVerticalOffsetDp: Float = -40f,
    val menuControlsGapDp: Float = 20f,
    val menuFrontOffsetDp: Float = 16f,
    val seekPreviewControlsGapDp: Float = 28f,
    val seekPreviewFrontOffsetDp: Float = 24f,
    val subtitleBaseUpOffsetDp: Float = 180f,
)

data class SeekPreviewRuntimeConfig(
    val bucketMs: Long = 2_000L,
    val memoryEntries: Int = 12,
    val frameHeight: Int = 270,
    val fallbackFrameWidth: Int = 480,
)

data class SubtitleRuntimeConfig(
    val defaultDistanceMeters: Float = 1.5f,
    val minDistanceMeters: Float = 0.7f,
    val maxDistanceMeters: Float = 2.0f,
    val defaultVerticalOffsetMeters: Float = 0.15f,
    val minVerticalOffsetMeters: Float = -0.15f,
    val maxVerticalOffsetMeters: Float = 0.15f,
    val defaultHorizontalOffsetMeters: Float = 0f,
    val minHorizontalOffsetMeters: Float = -0.30f,
    val maxHorizontalOffsetMeters: Float = 0.30f,
)

data class MetadataRuntimeConfig(
    val baseUrl: String = "https://jvrlibrary.com",
    val avWikiBaseUrl: String = "https://av-wiki.net",
    val requestIntervalMs: Long = 300L,
    val contentIdPattern: String = "(?i)([a-z]{2,10})[-_ ]?(\\d{2,6})(?!\\d)",
    val vrTokenPattern: String = "(?:VR|8K|8KVR|VR8K)",
    val videoExtensions: List<String> = listOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "mpg", "mpeg", "3gp", "ts", "m2ts",
    ),
    val imageExtensions: List<String> = listOf("jpg", "jpeg", "png", "webp"),
    val artworkPriority: List<String> = listOf(
        "nfoPoster",
        "nfoThumb",
        "sidecarPoster",
        "fanart",
        "cover",
        "folder",
        "generatedFrame",
    ),
)

data class CacheRuntimeConfig(
    val imageMemoryPercent: Double = 0.25,
    val imageDiskBytes: Long = 500L * 1024L * 1024L,
)

data class RemoteManifest(
    val schemaVersion: Int,
    val channel: String,
    val runtime: RuntimeConfig,
)

enum class RuntimeConfigSource {
    Bundled,
    Cache,
    Remote,
}

data class RuntimeConfigSnapshot(
    val source: RuntimeConfigSource,
    val manifestSha: String?,
    val fetchedAt: Long?,
    val config: RuntimeConfig,
)

object RuntimeConfigRegistry {
    private val _snapshot = MutableStateFlow(
        RuntimeConfigSnapshot(
            source = RuntimeConfigSource.Bundled,
            manifestSha = null,
            fetchedAt = null,
            config = RuntimeConfig(),
        )
    )
    val snapshot: StateFlow<RuntimeConfigSnapshot> = _snapshot.asStateFlow()
    val current: RuntimeConfig get() = _snapshot.value.config

    internal fun update(snapshot: RuntimeConfigSnapshot) {
        _snapshot.value = snapshot
    }
}
