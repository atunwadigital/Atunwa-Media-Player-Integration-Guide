# Atunwa Media SDK for Android

`:mediasdk` is an Android library for playing Atunwa live radio and TV streams, plus VOD
content, with built-in monetization. It wraps Jetpack Media3 (ExoPlayer) behind one
`MediaSDK` facade, fetches station configuration from the Atunwa backend, and ships drop-in UI:
a home-screen live card, a mini player, and a full-screen player with Picture-in-Picture and Cast.

```
com.atunwadigital.mediasdk          minSdk 24 · compileSdk 35 · Kotlin · Java 11
```

## Features

| Area | What you get |
|---|---|
| **Playback** | HLS live and VOD via Media3, background audio through a `MediaSessionService`, automatic audio focus, live-edge tracking, retries tuned for unreliable networks, Google Cast |
| **Configuration** | Station config fetched by API key (single station) or SDK key (all of a publisher's stations), cached in memory and on disk so the app can start offline |
| **State** | One reactive state machine (`StateFlow<SDKState>`) plus flows for ad state, the current item, play intent and audio amplitude |
| **UI** | `SDKLiveCardView`, `SDKMiniPlayerView`, the built-in `SDKPlayerActivity` (PiP, Cast, quality picker, share, related channels), and building blocks such as `SDKVideoView`, `SDKWaveformView` and `SDKProgressBar` |
| **Ads** | Google IMA prerolls with VAST companions, Google Ad Manager banners with Amazon APS header bidding, AdsWizz server-side-insertion companion banners, and IAB TCF consent forwarding |
| **Theming** | Per-station brand colors from the backend, layered over app-wide defaults |
| **News** | Client-side RSS/Atom aggregation of the publisher's registered news feeds |

---

## Quick integration guide

This section gets a single station playing in the built-in player. Each step links to the
detailed guide.

### 1. Add the dependency

The SDK is published to a public Maven repository; no account or token is needed:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven")
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.atunwadigital:mediasdk:1.1.0")
}
```

The AdsWizz SDK the SDK uses for companion banners comes from the same repository; you don't add
anything for it.

### 2. Configure keys

The SDK needs no keys of its own. For Google Ad Manager banners, add your Ad Manager app ID to
`local.properties` (which stays out of version control):

```properties
ADMOB_APP_ID=ca-app-pub-...~...    # optional: enables Google Ad Manager banners
```

If you want banners, declare the Ad Manager app ID in your app's manifest:

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="${ADMOB_APP_ID}" />
</application>
```

You don't need to declare anything else in the manifest. The SDK's own manifest already declares
the playback service, the player activity and the permissions, and they merge into your app. See
[Configuration](docs/configuration.md) for every key.

### 3. Set up the SDK at launch

```kotlin
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            MediaSDK.setup(applicationContext)   // builds the shared MediaController (idempotent)
            MediaSDK.preload(PLAYER_API_KEY)     // optional: warms the config cache
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}
```

### 4. Show the station and play it

```xml
<!-- activity_main.xml -->
<com.atunwadigital.mediasdk.ui.SDKLiveCardView
    android:id="@+id/liveCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />

<com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
    android:id="@+id/miniPlayer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:visibility="gone" />
```

```kotlin
// MainActivity.onCreate
findViewById<SDKLiveCardView>(R.id.liveCard).onCardClickListener = {
    // Opens the full-screen player immediately, then fetches config and starts playback.
    MediaSDK.launchPlayer(this, PLAYER_API_KEY)
}

findViewById<SDKMiniPlayerView>(R.id.miniPlayer).apply {
    onExpandListener = { MediaSDK.launchPlayer(this@MainActivity) }
    onCloseClickListener = { MediaSDK.reset() }
}
```

### 5. React to playback state (optional)

```kotlin
MediaSDK.state
    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
    .onEach { state ->
        miniPlayer.isVisible = state != SDKState.IDLE &&
            MediaSDK.currentMediaItem?.streamChannels?.isVideo == false
    }
    .launchIn(lifecycleScope)
```

The station now plays in the background, appears in the system media notification, and shows any
prerolls, banners and companions configured for it in the Atunwa dashboard. You don't need to
write any ad code.

> **Several stations?** Use publisher mode: `MediaSDK.initializePublisher(context, sdkKey)`, then
> `MediaSDK.loadPlayer(context, player)` when the user picks a station. See
> [Integration modes](docs/integration-modes.md#publisher-mode-mode-b).

---

## Detailed guides

| Guide | Covers |
|---|---|
| [Getting started](docs/getting-started.md) | Installation from the public Maven repository, manifest, initialization, attribution tag, ProGuard |
| [Configuration](docs/configuration.md) | Every `local.properties` key, what reads it, and how to supply keys from CI |
| [Integration modes](docs/integration-modes.md) | Single-player (one station) and publisher (many stations) mode, plus the lower-level `initialize` + `load` path |
| [Media playback & state](docs/media-playback.md) | Loading streams and custom `MediaItem`s, controls, the state machine and its transitions, observable flows, offline caching, pull-to-refresh, background playback, Cast |
| [UI components](docs/ui-components.md) | Every view the SDK ships: live card, mini player, full-screen player, video view, banner and companion views, building blocks |
| [Theming](docs/theming.md) | `SDKThemeConfig`, per-station colors, the color fallback chain, logos |
| [Advertising](docs/ad-integration.md) | IMA prerolls and companions, Ad Manager banners and Amazon APS, AdsWizz SSI companions, TCF consent, ad and banner sequencing, `IMAConfig` |
| [News feed](docs/news.md) | Fetching and displaying the publisher's RSS/Atom feeds |
| [API reference](docs/api-reference.md) | Every public type, function and property |
| [Troubleshooting](docs/troubleshooting.md) | Common problems, their causes, and the log tags to filter on |

## Architecture at a glance

```
            Host app (your Activities, Fragments, layouts)
                 │  calls                          ▲ collects StateFlows
                 ▼                                 │
  ┌──────────────────────────── MediaSDK (facade object) ────────────────────────────┐
  │ config fetch + cache · state machine · ad state · station switching · news      │
  └──────┬───────────────────────┬─────────────────────────┬──────────────────────────┘
         │ MediaController       │ ad providers            │ theme
         ▼                       ▼                         ▼
  CoreMediaService        ImaAdProvider (IMA)        SDKThemeManager
  (MediaSessionService,   SDKGptManager (GAM + APS)        │
   ExoPlayer / CastPlayer AdsWizzStreamCoordinator         ▼
   visualizer, TCF)       (SSI companions)           SDK UI views
```

- **Facade.** Host apps talk only to `MediaSDK`; they never touch Media3 directly.
- **Unidirectional data flow.** Views observe `MediaSDK`'s flows and call its methods. They
  never own the player. One `MediaController` is shared by every surface.
- **Playback first.** Every ad integration is best-effort. If an ad step fails or times out, the
  stream still plays.

## Requirements

- Android 7.0 (API 24) or later. PiP needs API 26+, and PiP auto-enter needs API 31+.
- AGP with Java 11 compatibility. The SDK is built with Kotlin 1.9.22.
- Network access to the Atunwa API, `https://amp.atunwadigital.com` by default.
- An Atunwa player API key (single-player mode) or publisher SDK key (publisher mode), from the
  Atunwa dashboard.
