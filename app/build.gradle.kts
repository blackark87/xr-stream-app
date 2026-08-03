import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    check(versionPropertiesFile.exists()) {
        "Missing version.properties at ${versionPropertiesFile.absolutePath}"
    }
    versionPropertiesFile.inputStream().use(::load)
}

data class PlayVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
)

val playVersionRaw = versionProperties.getProperty("PLAY_VERSION")?.trim().orEmpty()
check(playVersionRaw.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
    "PLAY_VERSION must be in MAJOR.MINOR.PATCH format, but was '$playVersionRaw'"
}
val playVersionParts = playVersionRaw.split('.')
val playVersion = PlayVersion(
    major = playVersionParts[0].toInt(),
    minor = playVersionParts[1].toInt(),
    patch = playVersionParts[2].toInt(),
)
check(playVersion.minor in 0..99) {
    "PLAY_VERSION minor must be between 0 and 99, but was ${playVersion.minor}"
}
check(playVersion.patch in 0..99) {
    "PLAY_VERSION patch must be between 0 and 99, but was ${playVersion.patch}"
}

val internalMinor = versionProperties.getProperty("INTERNAL_MINOR")?.trim()?.toIntOrNull()
    ?: error("INTERNAL_MINOR must be an integer.")
check(internalMinor in 0..99) {
    "INTERNAL_MINOR must be between 0 and 99, but was $internalMinor"
}
val releaseChannel = versionProperties.getProperty("RELEASE_CHANNEL")?.trim().orEmpty()
    .ifBlank { "internal" }

val internalVersionCode =
    playVersion.major * 1_000_000 +
            playVersion.minor * 10_000 +
            playVersion.patch * 100 +
            internalMinor
check(internalVersionCode in 1..2_100_000_000) {
    "Computed versionCode=$internalVersionCode is out of allowed range."
}
val internalDisplayVersion = "$releaseChannel.$internalMinor"
val internalVersionName =
    if (releaseChannel.equals("play", ignoreCase = true)) {
        playVersionRaw
    } else {
        "$playVersionRaw-$internalDisplayVersion"
    }
val internalBuildTimeUtc =
    ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

fun escapeForBuildConfig(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

fun resolveGitSha(): String {
    return runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootProject.rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        if (exitCode == 0 && output.isNotBlank()) {
            output
        } else {
            "unknown"
        }
    }.getOrDefault("unknown")
}

val internalGitSha = resolveGitSha()

android {
    namespace = "blackark.app.vr"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "blackark.app.vr"
        minSdk = 34
    targetSdk = 37
        versionCode = internalVersionCode
        versionName = internalVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PLAY_STORE_VERSION", "\"${escapeForBuildConfig(playVersionRaw)}\"")
        buildConfigField("int", "INTERNAL_MINOR_VERSION", internalMinor.toString())
        buildConfigField("String", "INTERNAL_DISPLAY_VERSION", "\"${escapeForBuildConfig(internalDisplayVersion)}\"")
        buildConfigField("String", "INTERNAL_VERSION_NAME", "\"${escapeForBuildConfig(internalVersionName)}\"")
        buildConfigField("int", "INTERNAL_VERSION_CODE", internalVersionCode.toString())
        buildConfigField("String", "INTERNAL_RELEASE_CHANNEL", "\"${escapeForBuildConfig(releaseChannel)}\"")
        buildConfigField("String", "INTERNAL_BUILD_TIME_UTC", "\"${escapeForBuildConfig(internalBuildTimeUtc)}\"")
        buildConfigField("String", "INTERNAL_GIT_SHA", "\"${escapeForBuildConfig(internalGitSha)}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // XR Libraries
    implementation(libs.androidx.xr.compose)
    implementation(libs.androidx.xr.runtime)
    implementation(libs.androidx.xr.scenecore)
    implementation(libs.androidx.xr.compose.material3)
    implementation(libs.androidx.xr.arcore)
    implementation(libs.androidx.xr.arcore.openxr)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // SMB Support
    implementation(libs.jcifs.ng)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.documentfile)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil for image/video loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.network.okhttp)
    implementation(libs.jsoup)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
