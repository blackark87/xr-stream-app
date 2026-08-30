import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionCodeEpochSeconds = 1_735_689_600L // 2025-01-01T00:00:00Z

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

val appVersion = versionProperties.getProperty("APP_VERSION")?.trim().orEmpty()
check(appVersion.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
    "APP_VERSION must be in MAJOR.MINOR.PATCH format, but was '$appVersion'"
}

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
val dmmClientId = resolveBuildSecret("DMM_CLIENT_ID")
val dmmClientSecret = resolveBuildSecret("DMM_CLIENT_SECRET")
val dmmRedirectUri = resolveBuildSecret("DMM_REDIRECT_URI")
val dmmJwtSecret = resolveBuildSecret("DMM_JWT_SECRET")
val dmmLibraryUrl = resolveBuildSecret("DMM_LIBRARY_URL")
    .ifBlank { "https://www.dmm.co.jp/digital/videoa/-/mylibrary/" }
val dmmDigitalApiBaseUrl = resolveBuildSecret("DMM_DIGITAL_API_BASE_URL")
    .ifBlank { "https://vr.digapi.dmm.com" }
val dmmDigitalApiAuthSecret = resolveBuildSecret("DMM_DIGITAL_API_AUTH_SECRET")
val dmmExploitIdPrefix = resolveBuildSecret("DMM_EXPLOIT_ID_PREFIX").ifBlank { "uid:" }
val dmmAppName = resolveBuildSecret("DMM_APP_NAME").ifBlank { "android_vr_store" }
val dmmApiAppVersion = resolveBuildSecret("DMM_API_APP_VERSION").ifBlank { appVersion }
val dmmDefaultDownloadQuality = resolveBuildSecret("DMM_DEFAULT_DOWNLOAD_QUALITY")
    .ifBlank { "high" }

listOf(
    "INTERNAL_GITHUB_PAT" to bundledGitHubPat,
    "DMM_CLIENT_ID" to dmmClientId,
    "DMM_CLIENT_SECRET" to dmmClientSecret,
    "DMM_REDIRECT_URI" to dmmRedirectUri,
    "DMM_JWT_SECRET" to dmmJwtSecret,
    "DMM_LIBRARY_URL" to dmmLibraryUrl,
    "DMM_DIGITAL_API_BASE_URL" to dmmDigitalApiBaseUrl,
    "DMM_DIGITAL_API_AUTH_SECRET" to dmmDigitalApiAuthSecret,
    "DMM_EXPLOIT_ID_PREFIX" to dmmExploitIdPrefix,
    "DMM_APP_NAME" to dmmAppName,
    "DMM_API_APP_VERSION" to dmmApiAppVersion,
    "DMM_DEFAULT_DOWNLOAD_QUALITY" to dmmDefaultDownloadQuality,
).forEach { (name, value) ->
    check('\n' !in value && '\r' !in value) { "$name must be a single-line value." }
}

val buildEpochSeconds =
    providers.environmentVariable("XR_BUILD_EPOCH_SECONDS").orNull?.trim()?.toLongOrNull()
        ?: Instant.now().epochSecond
val appVersionCodeLong = buildEpochSeconds - versionCodeEpochSeconds
check(appVersionCodeLong in 1..2_100_000_000L) {
    "Computed timestamp versionCode=$appVersionCodeLong is out of range."
}
val appVersionCode = appVersionCodeLong.toInt()

tasks.register("printAppVersion") {
    group = "versioning"
    doLast {
        println("APP_VERSION=$appVersion")
        println("VERSION_CODE=$appVersionCode")
    }
}

android {
    namespace = "blackark.app.vr"
    compileSdk {
        version = release(37)
    }

    // The provisioned WSD runtime still uses Android's legacy Apache HTTP compatibility classes.
    useLibrary("org.apache.http.legacy")

    defaultConfig {
        applicationId = "blackark.app.vr"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "APP_VERSION", "\"${escapeForBuildConfig(appVersion)}\"")
        buildConfigField("String", "BUNDLED_GITHUB_PAT", "\"${escapeForBuildConfig(bundledGitHubPat)}\"")
        buildConfigField("String", "DMM_CLIENT_ID", "\"${escapeForBuildConfig(dmmClientId)}\"")
        buildConfigField("String", "DMM_CLIENT_SECRET", "\"${escapeForBuildConfig(dmmClientSecret)}\"")
        buildConfigField("String", "DMM_REDIRECT_URI", "\"${escapeForBuildConfig(dmmRedirectUri)}\"")
        buildConfigField("String", "DMM_JWT_SECRET", "\"${escapeForBuildConfig(dmmJwtSecret)}\"")
        buildConfigField("String", "DMM_LIBRARY_URL", "\"${escapeForBuildConfig(dmmLibraryUrl)}\"")
        buildConfigField("String", "DMM_DIGITAL_API_BASE_URL", "\"${escapeForBuildConfig(dmmDigitalApiBaseUrl)}\"")
        buildConfigField("String", "DMM_DIGITAL_API_AUTH_SECRET", "\"${escapeForBuildConfig(dmmDigitalApiAuthSecret)}\"")
        buildConfigField("String", "DMM_EXPLOIT_ID_PREFIX", "\"${escapeForBuildConfig(dmmExploitIdPrefix)}\"")
        buildConfigField("String", "DMM_APP_NAME", "\"${escapeForBuildConfig(dmmAppName)}\"")
        buildConfigField("String", "DMM_API_APP_VERSION", "\"${escapeForBuildConfig(dmmApiAppVersion)}\"")
        buildConfigField("String", "DMM_DEFAULT_DOWNLOAD_QUALITY", "\"${escapeForBuildConfig(dmmDefaultDownloadQuality)}\"")
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
