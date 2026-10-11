import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Reads a value from the (git-ignored) root `spotify.properties` file. The value is baked into
 * `BuildConfig` at build time, so the app must be rebuilt + reinstalled after it changes.
 */
fun spotifyProps(name: String, default: String = ""): String {
    val props = Properties()
    val f = rootProject.file("spotify.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    return props.getProperty(name, default).trim()
}

android {
    namespace = "com.example.spotifyreplica"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.spotifyreplica"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${spotifyProps("SPOTIFY_CLIENT_ID")}\"")
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${spotifyProps("SPOTIFY_REDIRECT_URI", "spotifyreplica://callback")}\""
        )
        buildConfigField("String", "SPOTIFY_AUTH_URL", "\"https://accounts.spotify.com/authorize\"")
        buildConfigField("String", "SPOTIFY_TOKEN_URL", "\"https://accounts.spotify.com/api/token\"")
        buildConfigField("String", "BASE_URL_SPOTIFY", "\"https://api.spotify.com/v1/\"")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE,LICENSE.txt,NOTICE,NOTICE.txt}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // Core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    // Only material-icons-core (Icons.Default / Icons.Filled); NOT the extended icon set.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Settings / work
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Media3 (playback)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common.ktx)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Network (Spotify)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Auth (Custom Tabs) + tagging + coroutines
    implementation(libs.androidx.browser)
    implementation(libs.jaudiotagger)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
