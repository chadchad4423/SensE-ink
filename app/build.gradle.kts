import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Remote config: a small JSON file, fetched at launch, that can show an
// in-app message and flag when a newer version exists - without a release.
// Mirrors the pattern already used in TripTime (see that repo's
// RemoteConfig.kt). Override with CONFIG_URL in local.properties (gitignored)
// if testing against a fork or a not-yet-merged branch; the default points at
// this repo once it has a remote - until then the fetch just fails silently
// and the app carries on with no notice, which is the whole design point.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val configUrl: String = localProperties.getProperty(
    "CONFIG_URL",
    "https://raw.githubusercontent.com/chadchad4423/Sensi-eink/main/docs/config.json",
)

android {
    namespace = "com.chad.sensieink"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.chad.sensieink"
        // Verified against the Mudita Kompakt (KompaktAudioProbe release notes): Android 12 / API 31.
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CONFIG_URL", "\"$configUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    // Mudita Mindful Design - required UI library for this project, see sensi-client-spec.md.
    implementation(libs.mudita.mmd)

    // Sensi cloud protocol: OAuth over OkHttp, realtime state over socket.io.
    implementation(libs.squareup.okhttp3)
    implementation(libs.socketio.client)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
