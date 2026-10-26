# Getting started

This guide covers adding the SDK to an app and initializing it. When you finish, continue with
[Integration modes](integration-modes.md).

- [1. Installation](#1-installation)
- [2. Manifest](#2-manifest)
- [3. Configuration keys](#3-configuration-keys)
- [4. Initialize the SDK](#4-initialize-the-sdk)
- [5. Attribution tag (optional)](#5-attribution-tag-optional)
- [6. Code shrinking](#6-code-shrinking)

---

## 1. Installation

The SDK is published as `com.atunwadigital:mediasdk` to a public Maven repository hosted in
[atunwadigital/Atunwa-Media-Player-Integration-Guide](https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide), which also has the iOS SDK and both
platforms' guides. No GitHub account or token is needed. Each Android release is listed there as an
`android-<version>` release.

### 1.1 Add the repository and the dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven") {
            content {
                includeGroup("com.atunwadigital")
                includeGroup("com.adswizz")
            }
        }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.atunwadigital:mediasdk:1.1.0")
}
```

Or through your version catalog:

```toml
# gradle/libs.versions.toml
[versions]
atunwaMediasdk = "1.1.0"

[libraries]
atunwa-mediasdk = { group = "com.atunwadigital", name = "mediasdk", version.ref = "atunwaMediasdk" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.atunwa.mediasdk)
}
```

The SDK's other dependencies come from `google()` and `mavenCentral()`, except the AdsWizz SDK,
which Atunwa redistributes from the same repository (see §1.2).

### 1.2 About the AdsWizz SDK

The SDK uses AdsWizz's SDK for server-side-insertion companion banners. AdsWizz normally distributes
it only to its partners; Atunwa redistributes it, unmodified, from the same repository, so you don't
need an AdsWizz account or token. It's proprietary software of AdsWizz Inc.; see `com/adswizz/NOTICE.md`
on the repository's `maven` branch.

### 1.3 Dependencies the SDK brings in

You don't declare these yourself. They're listed so you can spot version conflicts:

| Dependency | Purpose |
|---|---|
| `androidx.media3:*` 1.3.1 | ExoPlayer, HLS, UI, session, IMA extension, Cast |
| `com.google.android.gms:play-services-ads` | Google Ad Manager banners |
| `com.google.android.gms:play-services-cast-framework`, `androidx.mediarouter` | Google Cast |
| `com.amazon.android:aps-sdk:9.9.2` | Amazon Publisher Services (TAM) header bidding |
| `com.adswizz:adswizz-sdk:8.2.10` | AdsWizz SSI session and companion banners (from the Atunwa repository) |
| `kotlinx-coroutines-android`, `kotlinx-serialization-json` | Flows and config parsing |
| `io.coil-kt:coil`, `com.airbnb.android:lottie` | Logo/artwork loading and loading animations |

## 2. Manifest

You don't need to add anything to the manifest for playback. The SDK's manifest merges these
into your app:

- **Permissions:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `WAKE_LOCK`, `RECORD_AUDIO`
- **`CoreMediaService`:** a `MediaSessionService` with `foregroundServiceType="mediaPlayback"`.
  It owns the player and the system media notification.
- **`SDKPlayerActivity`:** the built-in full-screen player, with PiP support.
- **Cast options provider** (`androidx.media3.cast.DefaultCastOptionsProvider`) and
  `MediaTransferReceiver` for output switching.
- **`com.google.android.gms.ads.AD_MANAGER_APP = true`**, which marks your app as an Ad Manager
  app so Google Mobile Ads starts without an AdMob or Ad Manager app ID. Banners need no app ID.
- **Removal of `MobileAdsInitProvider`**, so Google Mobile Ads doesn't start at process launch.
  `SDKGptManager` initializes it during `setup()` instead.

If your app already declares `com.google.android.gms.ads.APPLICATION_ID` (because it also shows its
own AdMob ads, for example), keep it. It works alongside the SDK's meta-data.

### About `RECORD_AUDIO`

The waveform visualizer reads the player's own output through `android.media.audiofx.Visualizer`,
and that API is gated on `RECORD_AUDIO`. The SDK never requests this permission at runtime and
doesn't record the microphone. If your app hasn't been granted the permission, the visualizer
fails silently and `SDKWaveformView` falls back to its synthetic animation. To get a waveform
that follows the real audio, request the permission yourself:

```kotlin
registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    .launch(Manifest.permission.RECORD_AUDIO)
```

If you don't want the permission at all, remove it in your app's manifest:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" tools:node="remove" />
```

## 3. Configuration keys

The SDK needs no keys in your app.

The SDK's own settings, such as `ATUNWA_SDK_BASE_URL` and `AMAZON_APP_ID`, are compiled into each
published version, so you don't set them in your app. See [Configuration](configuration.md) for the
full list and how to supply keys in CI.

## 4. Initialize the SDK

### 4.1 `setup()`: always first

`setup()` binds a `MediaController` to `CoreMediaService` and creates the ad managers (IMA,
GPT/APS, AdsWizz). Call it once early, from a splash screen or a launcher activity, before any SDK
UI is shown:

```kotlin
lifecycleScope.launch {
    MediaSDK.setup(applicationContext)
        .onFailure { Log.e("App", "MediaSDK setup failed", it) }
}
```

`setup()` is idempotent and returns immediately when it has already succeeded, so you can call it
again from any screen that might be reached first.

### 4.2 Choose an integration mode

| | Single-player (Mode A) | Publisher (Mode B) |
|---|---|---|
| Use when | Your app streams exactly one station | Your app offers a publisher's station list |
| Key | Player **API key** | Publisher **SDK key** |
| Init call | `initialize(context, apiKey)` or `preload(apiKey)` | `initializePublisher(context, sdkKey)` |
| Start playing | `launchPlayer(context, apiKey)` | `loadPlayer(context, player)` / `activatePlayer(context, player)` |
| Endpoint | `GET /sdk/v1/player?key=…` | `GET /sdk/v1/config?key=…` |

A complete splash screen that supports both modes:

```kotlin
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            MediaSDK.setup(applicationContext)

            if (BuildConfig.APP_MODE == "publisher") {
                MediaSDK.initializePublisher(applicationContext, BuildConfig.PUBLISHER_SDK_KEY)
                    .onFailure { /* non-fatal: the app can retry with refreshPublisherConfig() */ }
            } else {
                MediaSDK.preload(BuildConfig.PLAYER_KEY)
            }

            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
```

Details for each mode are in [Integration modes](integration-modes.md).

### 4.3 Optional: tune IMA before the first ad request

The IMA ads loader is built lazily the first time a stream with an ad tag loads, so any changes to
`MediaSDK.imaConfig` must happen before that:

```kotlin
MediaSDK.imaConfig = MediaSDK.imaConfig.copy(vastLoadTimeoutMs = 6_000, language = "fr")
```

See [Advertising → Tuning IMA](ad-integration.md#tuning-ima-imaconfig).

## 5. Attribution tag (optional)

The SDK's manifest declares `<attribution android:tag="atunwa_media_sdk" />`, and
`CoreMediaService` uses it. On Android 11+ you can make your own activities that host SDK views
report under the same tag, so the system attributes the SDK's data access, such as the
visualizer's audio capture, separately from your app's:

```kotlin
override fun getAttributionTag(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "atunwa_media_sdk" else null
```

## 6. Code shrinking

The SDK's own build doesn't minify. If you enable R8 in your app, keep the serializable config
models; the SDK parses them with `kotlinx.serialization`:

```proguard
# kotlinx.serialization models used to parse /sdk/v1 responses
-keep,includedescriptorclasses class com.atunwadigital.mediasdk.models.**$$serializer { *; }
-keepclassmembers class com.atunwadigital.mediasdk.models.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
```

The SDK ships keep rules for the AdsWizz SDK, so you don't add any for it.

Test a release build end to end, including config fetch, an ad-enabled station and Cast, before
you ship.

---

**Next:** [Integration modes](integration-modes.md) · [Configuration](configuration.md)
