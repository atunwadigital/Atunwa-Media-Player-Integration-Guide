import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Atunwa key comes from local.properties, which is git-ignored, so it never lands in source
// control. See local.properties.example.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun atunwaProperty(name: String, default: String = "") =
    (localProperties.getProperty(name) ?: default).trim()

android {
    namespace = "com.atunwadigital.examples.media"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atunwadigital.examples.media"
        minSdk = 24 // the SDK needs Android 7.0+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "ATUNWA_APP_MODE", "\"${atunwaProperty("ATUNWA_APP_MODE", "single")}\"")
        buildConfigField("String", "ATUNWA_PLAYER_KEY", "\"${atunwaProperty("ATUNWA_PLAYER_KEY")}\"")
        buildConfigField("String", "ATUNWA_PUBLISHER_SDK_KEY", "\"${atunwaProperty("ATUNWA_PUBLISHER_SDK_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.atunwa.mediasdk)
    // SDKLiveCardView extends CardView, but the SDK only declares CardView as a runtime
    // dependency. Referencing the view from Kotlin needs it at compile time too.
    implementation(libs.androidx.cardview)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
