import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The publisher SDK key comes from local.properties, which is git-ignored. See
// local.properties.example.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.atunwadigital.examples.mediahouse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atunwadigital.examples.mediahouse"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val key = (localProperties.getProperty("ATUNWA_PUBLISHER_SDK_KEY") ?: "").trim()
        buildConfigField("String", "ATUNWA_PUBLISHER_SDK_KEY", "\"$key\"")
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
