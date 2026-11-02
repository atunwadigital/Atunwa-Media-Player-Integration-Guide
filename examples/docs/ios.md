# iOS starter

`examples/ios` is a SwiftUI app that shows both integration modes: one station with a player API key,
or all of a publisher's stations with a publisher SDK key. You pick the mode at build time. Code is in
`AtunwaMediaExample/`.

- [Project setup](#project-setup)
- [Startup](#startup)
- [Single-player mode](#single-player-mode)
- [Publisher mode](#publisher-mode)
- [The SDK's views in SwiftUI](#the-sdks-views-in-swiftui)
- [Observing playback](#observing-playback)
- [Possibilities](#possibilities)

---

## Project setup

| File | What it does |
|---|---|
| `AtunwaMediaExample.xcodeproj` | Adds the **MediaSDK** Swift package from this repository, version 1.0.6 or later |
| `Config/Atunwa.xcconfig` | Declares the settings with empty defaults, then `#include?`s `Atunwa.local.xcconfig` (git-ignored) |
| `AtunwaMediaExample/Info.plist` | Background audio, landscape orientations, `GADApplicationIdentifier`, and the three Atunwa settings as `$(…)` variables |
| `AtunwaConfig.swift` | Reads the settings back from `Info.plist` into `.singlePlayer`, `.publisher` or `.missing` |

`Info.plist` keys that matter for the SDK:

| Key | Why |
|---|---|
| `UIBackgroundModes` → `audio` | Background playback, the lock screen and Picture-in-Picture |
| Landscape in `UISupportedInterfaceOrientations` | The built-in player turns video full screen in landscape |
| `GADApplicationIdentifier` = `$(GAD_APPLICATION_IDENTIFIER)` | Your Ad Manager app ID. Left empty, SDK 1.0.6+ uses Google's sample app ID so the app still runs, and logs a warning. Set yours before you ship |

The app targets iOS 16 for `NavigationStack`; the SDK itself supports iOS 15.

## Startup

There's no separate setup call on iOS. The app touches `MediaSDK.shared` in its `init`, so the
player, the lock-screen controls and the ad managers exist before the first stream loads:

```swift
init() {
    _ = MediaSDK.shared
    _playback = StateObject(wrappedValue: Playback())
}
```

It then shows `SinglePlayerView`, `PublisherView` or `MissingKeyView` depending on the configuration.

## Single-player mode

`SinglePlayerView.swift`. An app for one station.

**1. Fetch the station.** `initialize(apiKey:)` makes it the active station, so its name and logo
are available before anything plays. It's skipped when the station is already active:

```swift
if let active = MediaSDK.shared.playerConfig, active.assetKey == playerApiKey {
    station = active
} else {
    await initialize()
}
```

**2. Show it, then start it on demand.** Before anything plays, the screen shows its own station
card with **Listen live** and **Play here**:

```swift
private func start(openPlayer: Bool) {
    let sdk = MediaSDK.shared
    if sdk.currentMediaItem == nil {
        try? sdk.load()   // throws only when no station is active
    }
    if openPlayer {
        sdk.launchPlayer()
    }
}
```

`load()` is needed because `initialize()` doesn't load the stream, and the SDK treats the
initialized station as active, so `launchPlayer(apiKey:)` alone wouldn't start it. Once a stream is
loaded, the screen swaps its card for `SDKLiveCardViewRepresentable`.

**3. Recover after close.** The mini player's close button calls `reset()` and initializes again.

> If you don't need the station's details before playback starts, one call replaces steps 1 and 2:
> `MediaSDK.shared.launchPlayer(apiKey: playerApiKey)`.

## Publisher mode

`PublisherView.swift`. An app offering every station a publisher has.

**1. Fetch the publisher** with `initializePublisher(sdkKey:)`. It uses the memory or disk cache
when it has one, and doesn't start anything.

**2. Show the stations** in a `List`: the SDK's live card previewing the first station, a station
count with a segmented Radio/TV filter (only when the publisher has both), and one row per station.

**3. Two ways to play a station.**

| Action | Call | Result |
|---|---|---|
| Tap the row | `MediaSDK.shared.loadPlayer(station)` | Switches station and presents the SDK's full-screen player |
| Tap the row's play button | `MediaSDK.shared.activatePlayer(station)` | Switches station and plays in place; the mini player appears |

Neither passes a `from:` view controller: the SDK presents over the top-most view controller, which
works from SwiftUI.

**4. Pull to refresh** with `.refreshable` and `refreshPublisherConfig(sdkKey:)`, which always goes
to the network.

**5. Recover after close.** `reset()` clears `publisherConfig` too, so the view calls
`initializePublisher` again.

## The SDK's views in SwiftUI

The SDK ships `SDKLiveCardViewRepresentable`, used directly:

```swift
SDKLiveCardViewRepresentable(previewPlayer: defaultStation) {
    let sdk = MediaSDK.shared
    if sdk.playerConfig != nil {
        sdk.launchPlayer()
    } else {
        sdk.loadPlayer(defaultStation)
    }
}
```

There's no representable for the mini player, so `Components.swift` wraps `SDKMiniPlayerView` in a
small `UIViewRepresentable` and pins it to the bottom with `safeAreaInset`, at its native 62 pt height:

```swift
func miniPlayer(_ playback: Playback, onClose: @escaping () -> Void) -> some View {
    safeAreaInset(edge: .bottom, spacing: 0) {
        if playback.showsMiniPlayer {
            MiniPlayerBar(onClose: onClose).frame(height: 62)
        }
    }
}
```

## Observing playback

`Playback.swift` is an `ObservableObject` that combines four of the SDK's Combine publishers and
republishes them for SwiftUI:

```swift
sdk.statePublisher
    .combineLatest(sdk.playWhenReadyPublisher, sdk.isDedicatedPlayerActivePublisher, sdk.currentMediaItemPublisher)
    .receive(on: DispatchQueue.main)
    .sink { … }
```

From those it derives `hasStream`, `isPlaying`, the active station's asset key, and
`showsMiniPlayer` (a stream is loaded and the full-screen player isn't open; it counts as open while
in Picture-in-Picture).

## Possibilities

- **Quality picker in your own UI.** `availableQualities`, `selectQuality(_:)` and
  `selectedQualityPublisher` expose the stream's own variants, the same ones the built-in player
  offers.
- **Rewind live radio.** For streams with a DVR window, `canSeek()` and `seek(by:)` let you add
  "back 30 seconds" to your own controls.
- **Open video in portrait.** `MediaSDK.shared.videoFullscreenByDefault = false`.
- **Close instead of PiP.** `MediaSDK.shared.videoPipOnBack = false` closes the player instead of
  minimizing a playing video into Picture-in-Picture.
- **Embedded audio player.** `SDKAudioPlayerView` is a large-format "now playing" panel (artwork,
  waveform, volume, companion ad slot) to put inside your own screen instead of presenting the
  full-screen player.
- **Your own player screen.** `createVideoViewRepresentable()` gives a video surface with PiP set up.
  See [Building a custom player screen](../../ios/docs/media-playback.md#building-a-custom-player-screen).

More in [What else you can build](possibilities.md).
