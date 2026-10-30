# Getting started

This guide covers adding the `MediaSDK` package to an app and initializing it. When you finish,
continue with [Integration modes](integration-modes.md).

- [1. Installation](#1-installation)
- [2. Info.plist](#2-infoplist)
- [3. Initialize the SDK](#3-initialize-the-sdk)
- [4. SwiftUI or UIKit](#4-swiftui-or-uikit)

---

## 1. Installation

The SDK is distributed as a public Swift package from
[atunwadigital/Atunwa-Media-Player-Integration-Guide](https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide),
which also hosts the Android SDK and both platforms' guides. The package ships the SDK prebuilt (an
XCFramework); no GitHub account or token is needed. Each iOS release is a plain version tag on that
repository, such as `1.1.0`.

### 1.1 Add the package

**Xcode project:** **File → Add Package Dependencies…**, enter
`https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git`, choose a version rule, and add the **MediaSDK**
library to your app target.

**Package.swift:**

```swift
// swift-tools-version: 5.9
let package = Package(
    name: "MyApp",
    platforms: [.iOS(.v15)],
    dependencies: [
        .package(url: "https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git", from: "1.1.0"),
    ],
    targets: [
        .target(
            name: "MyApp",
            dependencies: [.product(name: "MediaSDK", package: "Atunwa-Media-Player-Integration-Guide")]
        ),
    ]
)
```

> Because the SDK is prebuilt, your app needs the same Xcode version that built it, or newer. Each
> release's notes say which Xcode built it.

### 1.2 What you get

Everything in these guides: playback, the player screens, IMA prerolls and companions, Ad Manager
banners, AdsWizz server-side-insertion companions and news.

### 1.3 About the AdsWizz SDK

The package includes the AdsWizz SDK (`AdswizzSDK.xcframework` and `AdswizzSDKCore.xcframework`),
which powers AdsWizz companion banners on live streams. It's proprietary software of AdsWizz Inc.,
redistributed unmodified by Atunwa for use with this SDK, so you don't need an AdsWizz account or
access to AdsWizz's repository. The terms are in `ios/third-party/ADSWIZZ-NOTICE.md` in the package
repository.

Both are dynamic frameworks: Xcode embeds and signs them in your app automatically. Don't add the
AdsWizz SDK to your app separately; two copies would clash.

### 1.4 Dependencies the SDK brings in

You don't declare these yourself. They're listed so you can spot version conflicts:

| Package | Purpose |
|---|---|
| `swift-package-manager-google-interactive-media-ads-ios` (from 3.0.0) | IMA prerolls and VAST companions |
| `swift-package-manager-google-mobile-ads` (from 12.0.0) | Google Ad Manager banners. Brings Google's User Messaging Platform with it |
| AdsWizz SDK (bundled, see [1.3](#13-about-the-adswizz-sdk)) | AdsWizz SSI sessions and companion banners |

The Google packages come from Google's repositories; the AdsWizz frameworks are downloaded from
this SDK's releases. All of them are linked for iOS only. The rest of the SDK uses system frameworks: AVFoundation, AVKit,
MediaPlayer, MediaToolbox (for the waveform's audio tap), Network and Combine.

## 2. Info.plist

| Key | Required? | Why |
|---|---|---|
| `UIBackgroundModes` → `audio` | **Yes** | Keeps playback going in the background and on the lock screen, and is what Picture-in-Picture needs. In Xcode: **Signing & Capabilities → Background Modes → Audio, AirPlay, and Picture in Picture** |
| Landscape orientations in `UISupportedInterfaceOrientations` | For full-screen video | The built-in player turns video full screen in landscape. If the app only allows portrait, the rotation request is refused and the video stays portrait. See [Full-screen video](media-playback.md#full-screen-video) |
| `GADApplicationIdentifier` | **Before you ship** | Your **Ad Manager** app ID, from **Inventory › Apps** in Ad Manager. No AdMob account is needed. Google Mobile Ads on iOS has no way to run without one, unlike Android. If it's missing or malformed, the SDK uses Google's sample app ID instead, so the app still runs and shows banners, and logs a warning. See below |
| `SKAdNetworkItems` | Recommended with banners | Google's recommended list for Ad Manager attribution. See Google's Mobile Ads documentation |

```xml
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>

<!-- Ad Manager app ID -->
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-xxxxxxxxxxxxxxxx~xxxxxxxxxx</string>
```

Google Mobile Ads stops the app at launch (`GADInvalidInitializationException`) when
`GADApplicationIdentifier` is missing or isn't in the issued `ca-app-pub-<digits>~<digits>` form,
just by being linked. To keep that from happening while you set up, the SDK checks the value as the
app launches. If it's missing, empty, malformed or an unresolved `$(VAR)` from an `.xcconfig`, the
SDK uses Google's sample app ID, `ca-app-pub-3940256099942544~1458002511`, logs

```
MediaSDK: no valid Ad Manager app ID (GADApplicationIdentifier) in Info.plist. Using Google's sample app ID ca-app-pub-3940256099942544~1458002511 so the app can run. Set your own Ad Manager app ID in Info.plist before you ship.
```

and sets `MediaSDK.shared.gptManager.isUsingTestAppId` to `true`. Playback, IMA prerolls,
companions, AdsWizz and banners all keep working. Add your own Ad Manager app ID before you
release, so banner impressions are attributed to your app.

The SDK doesn't need microphone, camera or tracking permissions. Its waveform reads the player's own
decoded audio, not the microphone.

## 3. Initialize the SDK

There's no separate setup call on iOS (Android's `setup()`). The first access to `MediaSDK.shared`
creates the player, the lock-screen controls and the ad managers. Touch it early, for example from
your splash screen, so the ad SDKs have started before the first stream loads.

All of `MediaSDK` is `@MainActor`. Call it from the main thread, or `await` it from async code.

### 3.1 Choose an integration mode

| | Single-player (Mode A) | Publisher (Mode B) |
|---|---|---|
| Use when | Your app streams exactly one station | Your app offers a publisher's station list |
| Key | Player **API key** | Publisher **SDK key** |
| Init call | `initialize(apiKey:)` or `preload(apiKey:)` | `initializePublisher(sdkKey:)` |
| Start playing | `launchPlayer(apiKey:from:)` | `loadPlayer(_:from:)` / `activatePlayer(_:)` |
| Endpoint | `GET /sdk/v1/player?key=…` | `GET /sdk/v1/config?key=…` |

A splash screen that supports both modes:

```swift
import SwiftUI
import MediaSDK

struct SplashView: View {
    @State private var ready = false

    var body: some View {
        Group {
            if ready { MainView() } else { ProgressView() }
        }
        .task {
            switch Config.appMode {
            case .publisher:
                // Non-fatal: the app can retry later with refreshPublisherConfig(sdkKey:).
                _ = try? await MediaSDK.shared.initializePublisher(
                    sdkKey: Config.publisherSdkKey,
                    themeConfig: Config.themeConfig
                )
            case .singlePlayer:
                await MediaSDK.shared.preload(apiKey: Config.playerKey)
            }
            ready = true
        }
    }
}
```

Details for each mode are in [Integration modes](integration-modes.md).

### 3.2 Optional: tune the player before it opens

```swift
MediaSDK.shared.imaConfig.vastLoadTimeoutMs = 6_000      // before the first ad-enabled load
MediaSDK.shared.imaConfig.language = "fr"
MediaSDK.shared.videoFullscreenByDefault = true          // open video full screen (default)
MediaSDK.shared.videoPipOnBack = true                    // closing a playing video goes to PiP (default)
```

See [Advertising → Tuning IMA](ad-integration.md#tuning-ima-imaconfig) and
[Media playback → Full-screen video](media-playback.md#full-screen-video).

## 4. SwiftUI or UIKit

The SDK's views are UIKit views. SwiftUI apps use the `…Representable` wrappers the SDK ships
(`SDKLiveCardViewRepresentable`, `SDKVideoViewRepresentable`, `SDKWaveformRepresentable`,
`SDKLiveBadgeRepresentable`, `SDKGptBannerViewRepresentable`), or wrap any other SDK view in a
`UIViewRepresentable` of their own.

The built-in full-screen player is a `UIViewController`. `MediaSDK.shared.launchPlayer(from:)`
presents it modally. Pass `nil` and it presents from the top-most view controller of the key window,
which works from SwiftUI too.

---

**Next:** [Integration modes](integration-modes.md) · [Configuration](configuration.md)
