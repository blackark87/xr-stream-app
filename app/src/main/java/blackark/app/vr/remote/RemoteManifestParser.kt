package blackark.app.vr.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object RemoteManifestParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parse(json: String): RemoteManifest {
        val root = JSONObject(json)
        val schemaVersion = root.requireInt("schemaVersion")
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported remote manifest schema $schemaVersion"
        }
        val channel = root.optString("channel", "internal").trim().ifBlank { "internal" }
        require(channel == "internal") { "Unsupported update channel '$channel'" }

        val minimumAppVersionCode = root.optLong("minimumAppVersionCode", 1L).coerceAtLeast(1L)
        require(minimumAppVersionCode <= MAX_ANDROID_VERSION_CODE) {
            "minimumAppVersionCode is too large"
        }
        val release = root.optJSONObject("release")?.let(::parseRelease)
        require(
            minimumAppVersionCode <= 1L ||
                (release != null && release.versionCode >= minimumAppVersionCode)
        ) { "A minimum app version requires a matching release" }

        return RemoteManifest(
            schemaVersion = schemaVersion,
            channel = channel,
            minimumAppVersionCode = minimumAppVersionCode,
            runtime = parseRuntime(root.optJSONObject("runtime") ?: JSONObject()).validated(),
            release = release,
        )
    }

    private fun parseRuntime(json: JSONObject): RuntimeConfig {
        val features = json.optJSONObject("features") ?: JSONObject()
        val defaults = json.optJSONObject("defaults") ?: JSONObject()
        val preview = json.optJSONObject("preview") ?: JSONObject()
        val playback = json.optJSONObject("playback") ?: JSONObject()
        val controller = json.optJSONObject("controller") ?: JSONObject()
        val headFollow = json.optJSONObject("headFollow") ?: JSONObject()
        val seekPreview = json.optJSONObject("seekPreview") ?: JSONObject()
        val subtitles = json.optJSONObject("subtitles") ?: JSONObject()
        val metadata = json.optJSONObject("metadata") ?: JSONObject()
        val cache = json.optJSONObject("cache") ?: JSONObject()
        val bundled = RuntimeConfig()

        return RuntimeConfig(
            features = FeatureRuntimeConfig(
                remoteUpdatesEnabled = features.optBoolean("remoteUpdatesEnabled", bundled.features.remoteUpdatesEnabled),
                motionPreviewEnabled = features.optBoolean("motionPreviewEnabled", bundled.features.motionPreviewEnabled),
                generatedSeekPreviewEnabled = features.optBoolean(
                    "generatedSeekPreviewEnabled",
                    bundled.features.generatedSeekPreviewEnabled,
                ),
            ),
            defaults = UserDefaultRuntimeConfig(
                backgroundIndexingEnabled = defaults.optBoolean(
                    "backgroundIndexingEnabled",
                    bundled.defaults.backgroundIndexingEnabled,
                ),
                handTrackingEnabled = defaults.optBoolean("handTrackingEnabled", bundled.defaults.handTrackingEnabled),
                playbackControlsHeadFollowEnabled = defaults.optBoolean(
                    "playbackControlsHeadFollowEnabled",
                    bundled.defaults.playbackControlsHeadFollowEnabled,
                ),
                libraryHoverPreviewEnabled = defaults.optBoolean(
                    "libraryHoverPreviewEnabled",
                    bundled.defaults.libraryHoverPreviewEnabled,
                ),
                smbHoverPreviewEnabled = defaults.optBoolean(
                    "smbHoverPreviewEnabled",
                    bundled.defaults.smbHoverPreviewEnabled,
                ),
            ),
            preview = PreviewRuntimeConfig(
                focusDelayMs = preview.optLong("focusDelayMs", bundled.preview.focusDelayMs),
                mainVideoDurationMs = preview.optLong(
                    "mainVideoDurationMs",
                    bundled.preview.mainVideoDurationMs,
                ),
                fanartIntervalMs = preview.optLong("fanartIntervalMs", bundled.preview.fanartIntervalMs),
                defaultVolume = preview.optDouble("defaultVolume", bundled.preview.defaultVolume.toDouble()).toFloat(),
            ),
            playback = PlaybackRuntimeConfig(
                controlsAutoHideMs = playback.optLong(
                    "controlsAutoHideMs",
                    bundled.playback.controlsAutoHideMs,
                ),
                seekStepMs = playback.optLong("seekStepMs", bundled.playback.seekStepMs),
                seekInitialRepeatMs = playback.optLong(
                    "seekInitialRepeatMs",
                    bundled.playback.seekInitialRepeatMs,
                ),
                seekRepeatMs = playback.optLong("seekRepeatMs", bundled.playback.seekRepeatMs),
                volumeStep = playback.optDouble("volumeStep", bundled.playback.volumeStep.toDouble()).toFloat(),
                volumeInitialRepeatMs = playback.optLong(
                    "volumeInitialRepeatMs",
                    bundled.playback.volumeInitialRepeatMs,
                ),
                volumeRepeatMs = playback.optLong("volumeRepeatMs", bundled.playback.volumeRepeatMs),
            ),
            controller = ControllerRuntimeConfig(
                axisEmitThreshold = controller.optDouble(
                    "axisEmitThreshold",
                    bundled.controller.axisEmitThreshold.toDouble(),
                ).toFloat(),
                axisProfileThreshold = controller.optDouble(
                    "axisProfileThreshold",
                    bundled.controller.axisProfileThreshold.toDouble(),
                ).toFloat(),
                deadZone = controller.optDouble("deadZone", bundled.controller.deadZone.toDouble()).toFloat(),
                engageThreshold = controller.optDouble(
                    "engageThreshold",
                    bundled.controller.engageThreshold.toDouble(),
                ).toFloat(),
                releaseThreshold = controller.optDouble(
                    "releaseThreshold",
                    bundled.controller.releaseThreshold.toDouble(),
                ).toFloat(),
                dominanceMargin = controller.optDouble(
                    "dominanceMargin",
                    bundled.controller.dominanceMargin.toDouble(),
                ).toFloat(),
                logIntervalMs = controller.optLong("logIntervalMs", bundled.controller.logIntervalMs),
            ),
            headFollow = HeadFollowRuntimeConfig(
                video180DeadZoneDegrees = headFollow.optDouble(
                    "video180DeadZoneDegrees",
                    bundled.headFollow.video180DeadZoneDegrees.toDouble(),
                ).toFloat(),
                controls360DeadZoneDegrees = headFollow.optDouble(
                    "controls360DeadZoneDegrees",
                    bundled.headFollow.controls360DeadZoneDegrees.toDouble(),
                ).toFloat(),
                video180Smoothing = headFollow.optDouble(
                    "video180Smoothing",
                    bundled.headFollow.video180Smoothing.toDouble(),
                ).toFloat(),
                controls360Smoothing = headFollow.optDouble(
                    "controls360Smoothing",
                    bundled.headFollow.controls360Smoothing.toDouble(),
                ).toFloat(),
                controlsDistanceDp = headFollow.optDouble(
                    "controlsDistanceDp",
                    bundled.headFollow.controlsDistanceDp.toDouble(),
                ).toFloat(),
                backgroundInputDistanceDp = headFollow.optDouble(
                    "backgroundInputDistanceDp",
                    bundled.headFollow.backgroundInputDistanceDp.toDouble(),
                ).toFloat(),
                controlsDownOffsetDp = headFollow.optDouble(
                    "controlsDownOffsetDp",
                    bundled.headFollow.controlsDownOffsetDp.toDouble(),
                ).toFloat(),
                flatPanelVerticalOffsetDp = headFollow.optDouble(
                    "flatPanelVerticalOffsetDp",
                    bundled.headFollow.flatPanelVerticalOffsetDp.toDouble(),
                ).toFloat(),
                menuControlsGapDp = headFollow.optDouble(
                    "menuControlsGapDp",
                    bundled.headFollow.menuControlsGapDp.toDouble(),
                ).toFloat(),
                menuFrontOffsetDp = headFollow.optDouble(
                    "menuFrontOffsetDp",
                    bundled.headFollow.menuFrontOffsetDp.toDouble(),
                ).toFloat(),
                seekPreviewControlsGapDp = headFollow.optDouble(
                    "seekPreviewControlsGapDp",
                    bundled.headFollow.seekPreviewControlsGapDp.toDouble(),
                ).toFloat(),
                seekPreviewFrontOffsetDp = headFollow.optDouble(
                    "seekPreviewFrontOffsetDp",
                    bundled.headFollow.seekPreviewFrontOffsetDp.toDouble(),
                ).toFloat(),
                subtitleBaseUpOffsetDp = headFollow.optDouble(
                    "subtitleBaseUpOffsetDp",
                    bundled.headFollow.subtitleBaseUpOffsetDp.toDouble(),
                ).toFloat(),
            ),
            seekPreview = SeekPreviewRuntimeConfig(
                bucketMs = seekPreview.optLong("bucketMs", bundled.seekPreview.bucketMs),
                memoryEntries = seekPreview.optInt("memoryEntries", bundled.seekPreview.memoryEntries),
                frameHeight = seekPreview.optInt("frameHeight", bundled.seekPreview.frameHeight),
                fallbackFrameWidth = seekPreview.optInt(
                    "fallbackFrameWidth",
                    bundled.seekPreview.fallbackFrameWidth,
                ),
            ),
            subtitles = SubtitleRuntimeConfig(
                defaultDistanceMeters = subtitles.optDouble(
                    "defaultDistanceMeters",
                    bundled.subtitles.defaultDistanceMeters.toDouble(),
                ).toFloat(),
                minDistanceMeters = subtitles.optDouble(
                    "minDistanceMeters",
                    bundled.subtitles.minDistanceMeters.toDouble(),
                ).toFloat(),
                maxDistanceMeters = subtitles.optDouble(
                    "maxDistanceMeters",
                    bundled.subtitles.maxDistanceMeters.toDouble(),
                ).toFloat(),
                defaultVerticalOffsetMeters = subtitles.optDouble(
                    "defaultVerticalOffsetMeters",
                    bundled.subtitles.defaultVerticalOffsetMeters.toDouble(),
                ).toFloat(),
                minVerticalOffsetMeters = subtitles.optDouble(
                    "minVerticalOffsetMeters",
                    bundled.subtitles.minVerticalOffsetMeters.toDouble(),
                ).toFloat(),
                maxVerticalOffsetMeters = subtitles.optDouble(
                    "maxVerticalOffsetMeters",
                    bundled.subtitles.maxVerticalOffsetMeters.toDouble(),
                ).toFloat(),
                defaultHorizontalOffsetMeters = subtitles.optDouble(
                    "defaultHorizontalOffsetMeters",
                    bundled.subtitles.defaultHorizontalOffsetMeters.toDouble(),
                ).toFloat(),
                minHorizontalOffsetMeters = subtitles.optDouble(
                    "minHorizontalOffsetMeters",
                    bundled.subtitles.minHorizontalOffsetMeters.toDouble(),
                ).toFloat(),
                maxHorizontalOffsetMeters = subtitles.optDouble(
                    "maxHorizontalOffsetMeters",
                    bundled.subtitles.maxHorizontalOffsetMeters.toDouble(),
                ).toFloat(),
            ),
            metadata = MetadataRuntimeConfig(
                baseUrl = metadata.optString("baseUrl", bundled.metadata.baseUrl),
                avWikiBaseUrl = metadata.optString(
                    "avWikiBaseUrl",
                    bundled.metadata.avWikiBaseUrl,
                ),
                requestIntervalMs = metadata.optLong(
                    "requestIntervalMs",
                    bundled.metadata.requestIntervalMs,
                ),
                contentIdPattern = metadata.optString(
                    "contentIdPattern",
                    bundled.metadata.contentIdPattern,
                ),
                vrTokenPattern = metadata.optString("vrTokenPattern", bundled.metadata.vrTokenPattern),
                videoExtensions = metadata.optJSONArray("videoExtensions")
                    ?.toStringList()
                    ?: bundled.metadata.videoExtensions,
                imageExtensions = metadata.optJSONArray("imageExtensions")
                    ?.toStringList()
                    ?: bundled.metadata.imageExtensions,
                artworkPriority = metadata.optJSONArray("artworkPriority")
                    ?.toStringList()
                    ?: bundled.metadata.artworkPriority,
            ),
            cache = CacheRuntimeConfig(
                imageMemoryPercent = cache.optDouble(
                    "imageMemoryPercent",
                    bundled.cache.imageMemoryPercent,
                ),
                imageDiskBytes = cache.optLong("imageDiskBytes", bundled.cache.imageDiskBytes),
            ),
        )
    }

    private fun parseRelease(json: JSONObject): RemoteRelease {
        val release = RemoteRelease(
            versionCode = json.requireLong("versionCode"),
            versionName = json.requireString("versionName"),
            gitSha = json.requireString("gitSha"),
            publishedAt = json.requireString("publishedAt"),
            assetId = json.requireLong("assetId"),
            sizeBytes = json.requireLong("sizeBytes"),
            sha256 = json.requireString("sha256").lowercase(),
            signingCertificateSha256 = json.requireString("signingCertificateSha256").lowercase(),
            mandatory = json.optBoolean("mandatory", false),
        )
        require(release.versionCode > 0L)
        require(release.versionCode <= MAX_ANDROID_VERSION_CODE)
        require(release.assetId > 0L)
        require(release.sizeBytes in 1L..MAX_APK_SIZE_BYTES)
        require(INTERNAL_VERSION_NAME_PATTERN.matches(release.versionName)) {
            "Invalid internal release version name"
        }
        require(GIT_SHA_PATTERN.matches(release.gitSha)) { "Invalid release Git SHA" }
        require(runCatching { Instant.parse(release.publishedAt) }.isSuccess) {
            "Invalid release publication time"
        }
        require(SHA_256_PATTERN.matches(release.sha256)) { "Invalid release SHA-256" }
        require(SHA_256_PATTERN.matches(release.signingCertificateSha256)) {
            "Invalid signing certificate SHA-256"
        }
        return release
    }

    private fun JSONObject.requireString(name: String): String =
        getString(name).trim().also { require(it.isNotBlank()) { "Missing '$name'" } }

    private fun JSONObject.requireInt(name: String): Int =
        getInt(name)

    private fun JSONObject.requireLong(name: String): Long =
        getLong(name)

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }

    private val SHA_256_PATTERN = Regex("^[0-9a-f]{64}$")
    private val GIT_SHA_PATTERN = Regex("^[0-9a-f]{7,40}$")
    private val INTERNAL_VERSION_NAME_PATTERN =
        Regex("^\\d+\\.\\d+\\.\\d+-internal\\.\\d{8}T\\d{6}\\+[0-9a-f]{7,40}$")
    private const val MAX_ANDROID_VERSION_CODE = 2_100_000_000L
    private const val MAX_APK_SIZE_BYTES = 2L * 1024L * 1024L * 1024L
}
