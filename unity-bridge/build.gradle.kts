plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val reuseAndroidSources = tasks.register<Sync>("reuseAndroidSources") {
    from(rootProject.file("app/src/main/java")) {
        include(
            "blackark/app/vr/data/database/**",
            "blackark/app/vr/data/model/LibraryVideoItem.kt",
            "blackark/app/vr/data/security/SmbCredentialStore.kt",
            "blackark/app/vr/network/SMBClient.kt",
            "blackark/app/vr/network/LocalFileClient.kt",
            "blackark/app/vr/network/SubtitleSidecarMetadata.kt",
            "blackark/app/vr/player/AssColorSubtitleParserFactory.kt",
            "blackark/app/vr/remote/RuntimeConfig.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/reused"))
}

android {
    namespace = "blackark.app.vr.unitybridge"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
androidComponents.onVariants { variant ->
    variant.sources.kotlin!!.addStaticSourceDirectory(layout.buildDirectory.dir("generated/reused").get().asFile.absolutePath)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
tasks.configureEach {
    if (name == "preBuild" || name.startsWith("ksp") || (name.startsWith("compile") && name.endsWith("Kotlin")))
        dependsOn(reuseAndroidSources)
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jcifs.ng)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.media3.extractor)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
}

// Resolve transitive dependencies through Unity's Maven graph to avoid duplicate AndroidX modules.
tasks.register<Sync>("exportUnityRuntime") {
    dependsOn("assembleDebug")
    from(layout.buildDirectory.file("outputs/aar/unity-bridge-debug.aar"))
    into(layout.buildDirectory.dir("unity-runtime"))
}
