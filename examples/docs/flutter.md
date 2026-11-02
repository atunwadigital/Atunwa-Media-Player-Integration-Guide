# Flutter starter

`examples/flutter` is a Flutter app for Android and iOS. The UI is Dart; playback, the player
screens and ads stay native, behind a small bridge the app owns. It's the bridge from the
[Flutter guide](../../flutter/README.md), extended with what the screens need.

- [How the pieces fit](#how-the-pieces-fit)
- [Native setup](#native-setup)
- [The bridge](#the-bridge)
- [Single-player mode](#single-player-mode)
- [Publisher mode](#publisher-mode)
- [Native views](#native-views)
- [Possibilities](#possibilities)

---

## How the pieces fit

```
lib/single_player_screen.dart ┐
lib/publisher_screen.dart     ├─▶ lib/atunwa_media.dart ──MethodChannel "atunwa/media"──────┐
lib/main.dart                 ┘    (AtunwaMedia, widgets) ──EventChannel "atunwa/media/state"─┤
                                                                                              ▼
              Android: AtunwaMediaBridge.kt, AtunwaViewFactories.kt  ──▶  MediaSDK (Android)
              iOS:     AtunwaMediaBridge.swift, AtunwaViewFactories.swift ──▶  MediaSDK (iOS)
```

Dart never touches a player. It calls a method, and listens to one stream of playback updates.

The mode and key come from `atunwa.json`, passed with `flutter run --dart-define-from-file=atunwa.json`
and read in `lib/config.dart` with `String.fromEnvironment`.

## Native setup

| Platform | What was added |
|---|---|
| Android | The Atunwa Maven repository in `android/build.gradle.kts`; `mediasdk`, `kotlinx-coroutines-android` and `androidx.cardview` in `android/app/build.gradle.kts`; `minSdk = 24`; R8 keep rules in `proguard-rules.pro` |
| Android | `MainActivity` creates the bridge and registers the two view factories in `configureFlutterEngine` |
| iOS | The **MediaSDK** Swift package on the **Runner** target (1.0.6 or later); background audio in `Info.plist` |
| iOS | `AppDelegate.didInitializeImplicitFlutterEngine` registers the bridge and the two view factories (the scene-based template) |

No `GADApplicationIdentifier` is set on iOS; SDK 1.0.6+ falls back to Google's sample app ID and logs
a warning. Add yours to `ios/Runner/Info.plist` before you ship.

## The bridge

`lib/atunwa_media.dart` is the only file the screens import. Each call maps to one SDK call on both
platforms:

| Dart | Android | iOS |
|---|---|---|
| `setup()` | `MediaSDK.setup(context)` | First access to `MediaSDK.shared` |
| `initialize(apiKey)` → `AtunwaStation` | `initialize(context, apiKey)`, then `playerConfig` | `initialize(apiKey:)`, then `playerConfig` |
| `activeStation()` → `AtunwaStation?` | `playerConfig` | `playerConfig` |
| `load()` | `load()`, unless something is loaded | `load()`, unless something is loaded |
| `openPlayer()` | `launchPlayer(activity)` | `launchPlayer()` |
| `launchPlayer(apiKey)` | `launchPlayer(activity, apiKey)` | `launchPlayer(apiKey:)` |
| `initializePublisher(sdkKey)` → `AtunwaPublisher` | `initializePublisher(context, sdkKey)` | `initializePublisher(sdkKey:)` |
| `refreshPublisher(sdkKey)` → `AtunwaPublisher` | `refreshPublisherConfig(context, sdkKey)` | `refreshPublisherConfig(sdkKey:)` |
| `loadStation(assetKey)` | `loadPlayer(activity, station)` | `loadPlayer(station)` |
| `activateStation(assetKey)` | `activatePlayer(context, station)` | `activatePlayer(station)` |
| `play()`, `pause()`, `reset()` | Same | Same |
| `playback` stream | `state`, `playWhenReady`, `currentMediaItemFlow`, `isDedicatedPlayerActive` combined | The four matching publishers combined |

**What crosses the channel.** Stations travel as maps (`assetKey`, `name`, `logo`, `isVideo`,
`publisherName`), never as SDK objects. `loadStation` and `activateStation` take an asset key, and
the native side looks the station up in `publisherConfig`. If it isn't there (after `reset()`), the
call fails with the error code `unknownStation`.

**Playback updates.** The event channel sends one map whenever any of the four values changes:

```dart
class AtunwaPlayback {
  final String state;            // idle, loading, playing, buffering, adPlaying, …
  final bool playWhenReady;
  final String? activeAssetKey;
  final bool isPlayerOpen;       // full-screen player, including Picture-in-Picture

  bool get hasStream => state != 'idle' && state != 'initializing';
  bool get showsMiniPlayer => hasStream && !isPlayerOpen;
}
```

State names are the same on both platforms: Android's `AD_PLAYING` is sent as `adPlaying`, matching
iOS's raw values.

**Compared with the guide's bridge**, this one adds `activeStation`, `load` and `refreshPublisher`;
`initialize` returns the station; `initializePublisher` returns the publisher's name and logo as well
as its stations; and the event channel reports the active station and whether the full-screen player
is open, not only the state.

## Single-player mode

`lib/single_player_screen.dart`. The same flow as the native starters: `initialize` to show the
station, then `load` (and `openPlayer` for **Listen live**) to start it. Before starting, it checks
`activeStation()` and initializes again if the mini player's close button has reset the SDK:

```dart
Future<void> _start({required bool openPlayer}) async {
  if (await AtunwaMedia.activeStation() == null) {
    await AtunwaMedia.initialize(widget.playerApiKey);
  }
  await AtunwaMedia.load();
  if (openPlayer) await AtunwaMedia.openPlayer();
}
```

## Publisher mode

`lib/publisher_screen.dart`. A `ListView` with the native live card, a Radio/TV `SegmentedButton`,
and a `StationTile` per station. A tap opens the full-screen player with `loadStation`; the play
button plays in place with `activateStation`. `RefreshIndicator` calls `refreshPublisher`.

After `reset()` the native side no longer has the publisher config, so station calls go through a
helper that initializes again and retries once:

```dart
Future<void> _withPublisher(Future<void> Function() action) async {
  try {
    await action();
  } on PlatformException catch (e) {
    if (e.code != 'unknownStation') rethrow;
    await AtunwaMedia.initializePublisher(widget.publisherSdkKey);
    await action();
  }
}
```

## Native views

The live card and mini player are platform views (`AndroidView` / `UiKitView`) registered as
`atunwa/live_card` and `atunwa/mini_player`. The native factories set their callbacks; Dart only
places them.

| Widget | Size | Notes |
|---|---|---|
| `AtunwaLiveCard` | `AspectRatio(16 / 9)` | Its factory reads `publisherConfig` when it's created, so it's built after `initializePublisher` |
| `AtunwaMiniPlayer` | 62 high on iOS, 76 on Android | The Android bar includes margins for its shadow; at 64 its subtitle is clipped. Shown while `showsMiniPlayer` |

Platform views cost more than ordinary widgets, so the app uses one card and one bar, never SDK views
inside list items.

## Possibilities

- **A news tab in Dart.** Add a `fetchNews` method to both bridges that returns the articles as maps
  (title, link, image, source, date), and render them with Flutter widgets.
- **Ad state in your UI.** Add a second event channel for `adPlaybackState` and `adCountdown` to show
  "Ad · 0:12" in your own controls.
- **On-demand content.** Add a `loadItem` method that builds a `MediaItem` from a URL, title and
  show name, to play your own videos and podcasts through the SDK (see [Media House](media-house.md)).
- **Highlight from native taps too.** The playback stream already reports `activeAssetKey`, so a
  station switched from the full-screen player's related-channels rail updates your list.
- **A Flutter package.** Move the bridge into a plugin package your apps share. The method names and
  map shapes are already platform-neutral.

More in [What else you can build](possibilities.md).
