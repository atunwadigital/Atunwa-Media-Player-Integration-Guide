# Using the Atunwa Media SDK from Flutter

There's no Flutter package for the Atunwa Media SDK yet. You can still use it today: add the native
[iOS](../ios/README.md) and [Android](../android/README.md) SDKs to your app's `ios/` and
`android/` projects, and call them from Dart through a small bridge that lives in your app. These
guides give you that bridge, ready to copy.

```
Dart  ──MethodChannel / EventChannel──▶  Kotlin bridge  ──▶  MediaSDK (Android)
                                    └─▶  Swift bridge   ──▶  MediaSDK (iOS)
```

Playback, background audio, lock-screen controls, the full-screen player and ads all run natively,
so they behave exactly as they do in a native app. Your Dart code only starts things and listens
to state.

## What you can bridge

| Level | Features | What it takes | Guide |
|---|---|---|---|
| **1. Launch the player** | The built-in full-screen player, with its ads, PiP, casting and AirPlay | A method channel with a couple of methods | [Getting started](docs/getting-started.md) |
| **2. Control and observe** | `play`, `pause`, `reset`, playback state, a publisher's station list | The same method channel plus an event channel | [Getting started](docs/getting-started.md) |
| **3. Embed native views** | The live card and the mini player inside your Flutter screens | Platform views | [Native views](docs/native-views.md) |

Most apps only need levels 1 and 2: show your station list in Flutter, and let the SDK's own
full-screen player do the rest.

## The bridge's Dart API

Both platform halves of the bridge implement the same channel contract, so your Dart code is the
same on iOS and Android:

```dart
await AtunwaMedia.setup();                                  // once, at startup
await AtunwaMedia.launchPlayer('YOUR_PLAYER_API_KEY');      // single station

final stations = await AtunwaMedia.initializePublisher('YOUR_PUBLISHER_SDK_KEY');
await AtunwaMedia.loadStation(stations.first.assetKey);     // opens the player

AtunwaMedia.state.listen((state) => print(state));          // "playing", "buffering", "adPlaying", …
```

| Dart call | Android | iOS |
|---|---|---|
| `setup()` | `MediaSDK.setup(context)` | First access to `MediaSDK.shared` |
| `initialize(apiKey)` | `MediaSDK.initialize(context, apiKey)` | `MediaSDK.shared.initialize(apiKey:)` |
| `launchPlayer(apiKey)` | `MediaSDK.launchPlayer(activity, apiKey)` | `MediaSDK.shared.launchPlayer(apiKey:)` |
| `openPlayer()` | `MediaSDK.launchPlayer(activity)` | `MediaSDK.shared.launchPlayer()` |
| `initializePublisher(sdkKey)` | `MediaSDK.initializePublisher(context, sdkKey)` | `MediaSDK.shared.initializePublisher(sdkKey:)` |
| `loadStation(assetKey)` | `MediaSDK.loadPlayer(activity, station)` | `MediaSDK.shared.loadPlayer(station)` |
| `activateStation(assetKey)` | `MediaSDK.activatePlayer(context, station)` | `MediaSDK.shared.activatePlayer(station)` |
| `play()`, `pause()`, `reset()` | `MediaSDK.play()`, … | `MediaSDK.shared.play()`, … |
| `state` stream | `MediaSDK.state` | `MediaSDK.shared.statePublisher` |

The concepts behind each call (integration modes, the state machine, ads, theming) are the same as
in the native SDKs, so the native guides apply as-is:

| Topic | iOS | Android |
|---|---|---|
| One station or a whole publisher | [Integration modes](../ios/docs/integration-modes.md) | [Integration modes](../android/docs/integration-modes.md) |
| Playback state | [Media playback](../ios/docs/media-playback.md) | [Media playback](../android/docs/media-playback.md) |
| Ads | [Advertising](../ios/docs/ad-integration.md) | [Advertising](../android/docs/ad-integration.md) |
| Colors and branding | [Theming](../ios/docs/theming.md) | [Theming](../android/docs/theming.md) |

## Guides

- [Getting started](docs/getting-started.md): add the native SDKs, then copy the bridge
- [Native views](docs/native-views.md): embed the live card and mini player
- [Troubleshooting](docs/troubleshooting.md): problems specific to Flutter apps
