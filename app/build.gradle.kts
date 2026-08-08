import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val internalVersionEpochSeconds = 1_735_689_600L // 2025-01-01T00:00:00Z

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    check(versionPropertiesFile.exists()) {
        "Missing version.properties at ${versionPropertiesFile.absolutePath}"
    }
    versionPropertiesFile.inputStream().use(::load)
}

val localSecretsFile = rootProject.file("secrets.local.properties")
val localSecrets = Properties().apply {
    if (localSecretsFile.exists()) {
        localSecretsFile.inputStream().use(::load)
    }
}

val playVersionRaw = versionProperties.getProperty("PLAY_VERSION")?.trim().orEmpty()
check(playVersionRaw.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
    "PLAY_VERSION must be in MAJOR.MINOR.PATCH format, but was '$playVersionRaw'"
}
val releaseChannel = versionProperties.getProperty("RELEASE_CHANNEL")?.trim().orEmpty()
    .ifBlank { "internal" }

fun escapeForBuildConfig(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

fun resolveBuildSecret(name: String): String =
    providers.environmentVariable(name).orNull
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: localSecrets.getProperty(name)?.trim().orEmpty()

val bundledGitHubPat = resolveBuildSecret("INTERNAL_GITHUB_PAT")
check('\n' !in bundledGitHubPat && '\r' !in bundledGitHubPat) {
    "INTERNAL_GITHUB_PAT must be a single-line value."
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
val internalBuildEpochSeconds =
    providers.environmentVariable("XR_BUILD_EPOCH_SECONDS").orNull?.trim()?.toLongOrNull()
        ?: Instant.now().epochSecond
val internalVersionCodeLong = internalBuildEpochSeconds - internalVersionEpochSeconds
check(internalVersionCodeLong in 1..2_100_000_000L) {
    "Computed timestamp versionCode=$internalVersionCodeLong is out of range."
}
val internalVersionCode = internalVersionCodeLong.toInt()
val internalBuildInstant = Instant.ofEpochSecond(internalBuildEpochSeconds)
val internalBuildId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    .withZone(ZoneOffset.UTC)
    .format(internalBuildInstant)
val internalDisplayVersion = "$releaseChannel.$internalBuildId"
val internalVersionName =
    if (releaseChannel.equals("play", ignoreCase = true)) {
        playVersionRaw
    } else {
        "$playVersionRaw-$internalDisplayVersion+$internalGitSha"
    }
val internalBuildTimeUtc = DateTimeFormatter.ISO_INSTANT.format(internalBuildInstant)

tasks.register("printInternalVersion") {
    group = "versioning"
    doLast {
        println("PLAY_VERSION=$playVersionRaw")
        println("RELEASE_CHANNEL=$releaseChannel")
        println("INTERNAL_VERSION_CODE=$internalVersionCode")
        println("INTERNAL_VERSION_NAME=$internalVersionName")
        println("INTERNAL_DISPLAY_VERSION=$internalDisplayVersion")
        println("INTERNAL_GIT_SHA=$internalGitSha")
        println("INTERNAL_BUILD_TIME_UTC=$internalBuildTimeUtc")
    }
}

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
        buildConfigField("String", "INTERNAL_DISPLAY_VERSION", "\"${escapeForBuildConfig(internalDisplayVersion)}\"")
        buildConfigField("String", "INTERNAL_VERSION_NAME", "\"${escapeForBuildConfig(internalVersionName)}\"")
        buildConfigField("int", "INTERNAL_VERSION_CODE", internalVersionCode.toString())
        buildConfigField("String", "INTERNAL_RELEASE_CHANNEL", "\"${escapeForBuildConfig(releaseChannel)}\"")
        buildConfigField("String", "INTERNAL_BUILD_TIME_UTC", "\"${escapeForBuildConfig(internalBuildTimeUtc)}\"")
        buildConfigField("String", "INTERNAL_GIT_SHA", "\"${escapeForBuildConfig(internalGitSha)}\"")
        buildConfigField("String", "BUNDLED_GITHUB_PAT", "\"${escapeForBuildConfig(bundledGitHubPat)}\"")
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
    // Jetpack XR beta01 transitively requests ARCore 1.53.0, whose malformed
    // constructor stack maps trigger D8 warnings. ARCore 1.54.0 fixes them.
    implementation(libs.google.ar.core)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.inspector)
    implementation(libs.androidx.media3.inspector.frame)

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
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
