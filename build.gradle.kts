import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val versionPropertiesFile = rootProject.file("version.properties")

fun loadVersionProperties(): Properties {
    check(versionPropertiesFile.exists()) {
        "Missing version.properties at ${versionPropertiesFile.absolutePath}"
    }
    return Properties().apply {
        versionPropertiesFile.inputStream().use(::load)
    }
}

data class PlayVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
)

fun parsePlayVersion(raw: String): PlayVersion {
    check(raw.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
        "PLAY_VERSION must be in MAJOR.MINOR.PATCH format, but was '$raw'"
    }
    val parts = raw.split('.')
    return PlayVersion(
        major = parts[0].toInt(),
        minor = parts[1].toInt(),
        patch = parts[2].toInt(),
    )
}

fun computeVersionCode(
    playVersion: PlayVersion,
    internalMinor: Int,
): Int {
    check(playVersion.minor in 0..99) {
        "PLAY_VERSION minor must be between 0 and 99, but was ${playVersion.minor}"
    }
    check(playVersion.patch in 0..99) {
        "PLAY_VERSION patch must be between 0 and 99, but was ${playVersion.patch}"
    }
    check(internalMinor in 0..99) {
        "INTERNAL_MINOR must be between 0 and 99, but was $internalMinor"
    }

    val code =
        playVersion.major * 1_000_000 +
                playVersion.minor * 10_000 +
                playVersion.patch * 100 +
                internalMinor
    check(code in 1..2_100_000_000) {
        "Computed versionCode=$code is out of allowed range."
    }
    return code
}

fun resolveInternalVersionName(
    playVersionRaw: String,
    releaseChannel: String,
    internalMinor: Int,
): String {
    return if (releaseChannel.equals("play", ignoreCase = true)) {
        playVersionRaw
    } else {
        "$playVersionRaw-$releaseChannel.$internalMinor"
    }
}

tasks.register("printInternalVersion") {
    group = "versioning"
    description = "Print internal version values resolved from version.properties."
    doLast {
        val properties = loadVersionProperties()
        val playVersionRaw = properties.getProperty("PLAY_VERSION")?.trim().orEmpty()
        val internalMinorRaw = properties.getProperty("INTERNAL_MINOR")?.trim().orEmpty()
        val releaseChannel = properties.getProperty("RELEASE_CHANNEL")?.trim().orEmpty()
            .ifBlank { "internal" }
        val internalMinor = internalMinorRaw.toIntOrNull()
            ?: error("INTERNAL_MINOR must be an integer, but was '$internalMinorRaw'")
        val playVersion = parsePlayVersion(playVersionRaw)
        val versionCode = computeVersionCode(playVersion, internalMinor)
        val versionName = resolveInternalVersionName(
            playVersionRaw = playVersionRaw,
            releaseChannel = releaseChannel,
            internalMinor = internalMinor,
        )

        println("PLAY_VERSION=$playVersionRaw")
        println("INTERNAL_MINOR=$internalMinor")
        println("RELEASE_CHANNEL=$releaseChannel")
        println("INTERNAL_VERSION_CODE=$versionCode")
        println("INTERNAL_VERSION_NAME=$versionName")
        println("INTERNAL_DISPLAY_VERSION=$releaseChannel.$internalMinor")
    }
}

tasks.register("bumpInternalVersion") {
    group = "versioning"
    description = "Increment INTERNAL_MINOR in version.properties."
    doLast {
        val properties = loadVersionProperties()
        val currentInternalMinorRaw = properties.getProperty("INTERNAL_MINOR")?.trim().orEmpty()
        val currentInternalMinor = currentInternalMinorRaw.toIntOrNull()
            ?: error("INTERNAL_MINOR must be an integer, but was '$currentInternalMinorRaw'")
        val nextInternalMinor = currentInternalMinor + 1
        properties.setProperty("INTERNAL_MINOR", nextInternalMinor.toString())

        versionPropertiesFile.writer().use { writer ->
            properties.store(writer, "Internal release version")
        }

        println("INTERNAL_MINOR bumped: $currentInternalMinor -> $nextInternalMinor")
        println("Updated at ${ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}")
    }
}
