# Atunwa Media SDK for iOS

`MediaSDK` is a Swift package for playing Atunwa live radio and TV streams, plus VOD content, with
built-in monetization. It wraps `AVPlayer` behind one `MediaSDK.shared` facade, fetches station
configuration from the Atunwa backend, and ships drop-in UI: a live card, a mini player, and a
full-screen player with full-screen landscape video, Picture-in-Picture and AirPlay.

It's the iOS counterpart of the Android SDK (`com.atunwadigital:mediasdk`). The public API is
deliberately the same shape on both platforms, so host apps write the same logic twice rather than
two different integrations.

```
MediaSDK (Swift package) · iOS 15+ · Swift 6 · UIKit + SwiftUI wrappers
```

## Features

| Area | What you get |
|---|---|
| **Playback** | HLS live and VOD via `AVPlayer`, background audio, lock-screen and Control Center controls, audio-session interruption handling, retries and stall recovery tuned for unreliable networks, AirPlay |
| **Configuration** | Station config fetched by API key (single station) or SDK key (all of a publisher's stations), cached in memory and on disk so the app can start offline |
| **State** | One reactive state machine (`statePublisher`) plus publishers for play intent, ad state, the current item, ad countdown and audio amplitude |
| **UI** | `SDKLiveCardView`, `SDKMiniPlayerView`, the built-in `SDKPlayerViewController` (full-screen landscape video, rotate-to-fullscreen, PiP, AirPlay, quality picker, share, related channels), `SDKAudioPlayerView`, and building blocks such as `SDKVideoView`, `SDKWaveformView` and `SDKLiveBadgeView`. SwiftUI `UIViewRepresentable` wrappers for the common ones |
| **Ads** | Google IMA prerolls with VAST companions, Google Ad Manager banners (with a seam for Amazon APS header bidding), and AdsWizz server-side-insertion companion banners |
| **Theming** | App-wide colors through `SDKThemeConfig`, plus the active station's logo |
| **News** | Client-side RSS/Atom aggregation of the publisher's registered news feeds |

---

## Quick integration guide

This section gets a single station playing in the built-in player. Each step links to the detailed
guide.

### 1. Add the package

In Xcode, choose **File → Add Package Dependencies…** and enter:

```
https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git
```

Or in a `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git", from: "1.1.0"),
],
targets: [
    .target(name: "MyApp", dependencies: [.product(name: "MediaSDK", package: "Atunwa-Media-Player-Integration-Guide")]),
]
```

No account or token is needed: the package is public and ships the SDK prebuilt, together with
the AdsWizz SDK it uses (see [Getting started → About the AdsWizz SDK](docs/getting-started.md#13-about-the-adswizz-sdk)).

### 2. Configure Info.plist

```xml
<!-- Keep playing in the background and allow Picture-in-Picture -->
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>

<!-- Optional: your Ad Manager app ID (Ad Manager › Inventory › Apps), enables banners.
     No AdMob account is needed. -->
<key>GADApplicationIdentifier</key>
<string>ca-app-pub-xxxxxxxxxxxxxxxx~xxxxxxxxxx</string>
```

To let video open full screen in landscape, the app must also support the landscape orientations
(**Target → General → Device Orientation**). See [Configuration](docs/configuration.md) for every key.

### 3. Show the station and play it

```swift
import MediaSDK

final class HomeViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        let card = SDKLiveCardView()
        card.onCardClickListener = { [weak self] in
            // Opens the full-screen player immediately, then fetches config and starts playback.
            MediaSDK.shared.launchPlayer(apiKey: PLAYER_API_KEY, from: self)
        }
        view.addSubview(card)

        let miniPlayer = SDKMiniPlayerView()
        miniPlayer.onExpandListener = { [weak self] in MediaSDK.shared.launchPlayer(from: self) }
        miniPlayer.onCloseClickListener = { MediaSDK.shared.reset() }
        view.addSubview(miniPlayer)
        // …constraints…
    }
}
```

In SwiftUI, use `SDKLiveCardViewRepresentable(previewPlayer:onCardClickListener:)` the same way.

### 4. React to playback state (optional)

```swift
MediaSDK.shared.statePublisher
    .combineLatest(MediaSDK.shared.isDedicatedPlayerActivePublisher)
    .receive(on: DispatchQueue.main)
    .sink { [weak self] state, playerOnScreen in
        // The player screen (full screen or in PiP) is the "big" player; show the bar otherwise.
        self?.miniPlayer.isHidden = state == .idle || playerOnScreen
    }
    .store(in: &cancellables)
```

The station now plays in the background, shows on the lock screen and in Control Center, and shows
any prerolls, banners and companions configured for it in the Atunwa dashboard. You don't need to
write any ad code.

> **Several stations?** Use publisher mode: `try await MediaSDK.shared.initializePublisher(sdkKey:)`,
> then `MediaSDK.shared.loadPlayer(player)` when the user picks a station. See
> [Integration modes](docs/integration-modes.md#publisher-mode-mode-b).

---

## Detailed guides

| Guide | Covers |
|---|---|
| [Getting started](docs/getting-started.md) | Installation with Swift Package Manager, the bundled AdsWizz SDK, Info.plist, initialization |
| [Configuration](docs/configuration.md) | Every Info.plist key and build setting the SDK reads, and what's compiled into the SDK |
| [Integration modes](docs/integration-modes.md) | Single-player (one station) and publisher (many stations) mode, plus the lower-level `initialize` + `load` path |
| [Media playback & state](docs/media-playback.md) | Loading streams and custom `MediaItem`s, controls, the state machine, publishers, offline start, background audio, PiP, AirPlay, quality and seeking |
| [UI components](docs/ui-components.md) | Every view the SDK ships: live card, mini player, full-screen player, audio player, video view, ad views, building blocks |
| [Theming](docs/theming.md) | `SDKThemeConfig`, the station logo, reading the theme in your own UI |
| [Advertising](docs/ad-integration.md) | IMA prerolls and companions, Ad Manager banners and the APS seam, AdsWizz SSI companions, ad sequencing, `IMAConfig` |
| [News feed](docs/news.md) | Fetching and displaying the publisher's RSS/Atom feeds |
| [API reference](docs/api-reference.md) | Every public type, function and property |
| [Troubleshooting](docs/troubleshooting.md) | Common problems, their causes, and the log lines to look for |

## Architecture at a glance

```
            Host app (your view controllers / SwiftUI views)
                 │  calls                          ▲ subscribes to publishers
                 ▼                                 │
  ┌──────────────────────────── MediaSDK.shared (facade) ────────────────────────────┐
  │ config fetch + cache · state machine · ad state · station switching · PiP · news │
  └──────┬───────────────────────┬─────────────────────────┬──────────────────────────┘
         │ AVPlayer              │ ad providers            │ theme
         ▼                       ▼                         ▼
  CorePlayerAdapter        IMAAdProvider (IMA)        SDKThemeManager
  (AVPlayer, audio         SDKBannerAdView (GAM)            │
   session, lock screen,   AdsWizzStreamCoordinator         ▼
   audio tap, PiP)         (SSI companions)           SDK UI views
```

- **Facade.** Host apps talk only to `MediaSDK.shared`; they never drive `AVPlayer` directly.
- **Unidirectional data flow.** Views subscribe to `MediaSDK`'s publishers and call its methods. They
  never own the player. One `AVPlayer` is shared by every surface.
- **Main actor.** `MediaSDK` and every SDK view are `@MainActor`. Call them from the main thread.
- **Playback first.** Every ad integration is best-effort. If an ad step fails or times out, the
  stream still plays.

## Requirements

- iOS 15 or later. Full-screen rotation requests use `requestGeometryUpdate` on iOS 16+, with a
  fallback on iOS 15. Automatic PiP when leaving the app needs iOS 14.2+ (always true here).
- Xcode with Swift 6.3 (the package's `swift-tools-version`). Google Mobile Ads needs Xcode 16+.
- Network access to the Atunwa API, `https://amp.atunwadigital.com`.
- An Atunwa player API key (single-player mode) or publisher SDK key (publisher mode), from the
  Atunwa dashboard.
