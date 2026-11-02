# Flutter example

A Flutter app (Android and iOS) using the Atunwa Media SDK through the bridge from the
[Flutter guide](../../flutter/README.md). See the [examples overview](../README.md) for what the two
modes show.

| File | What it shows |
|---|---|
| [`lib/atunwa_media.dart`](lib/atunwa_media.dart) | The Dart API, plus the live card and mini player widgets |
| [`lib/single_player_screen.dart`](lib/single_player_screen.dart) | Single-player mode, with a player API key |
| [`lib/publisher_screen.dart`](lib/publisher_screen.dart) | Publisher mode, with a publisher SDK key |
| [`android/app/src/main/kotlin/…/AtunwaMediaBridge.kt`](android/app/src/main/kotlin/com/atunwadigital/examples/atunwa_flutter_example/AtunwaMediaBridge.kt) | The Android bridge |
| [`ios/Runner/AtunwaMediaBridge.swift`](ios/Runner/AtunwaMediaBridge.swift) | The iOS bridge |
| `AtunwaViewFactories.kt` / `.swift` | The native views |

The bridge here extends the guide's bridge with what the screens need:

- `initialize()` returns the station.
- `activeStation()`, `load()` and `refreshPublisher()` are new.
- `initializePublisher()` returns the publisher as well as its stations.
- The event channel reports the active station and whether the full-screen player is open, not
  just the state.

## Run it

1. Copy `atunwa.example.json` to `atunwa.json` (git-ignored) and set the mode and your key:

   ```json
   {
     "ATUNWA_APP_MODE": "single",
     "ATUNWA_PLAYER_KEY": "…",
     "ATUNWA_PUBLISHER_SDK_KEY": ""
   }
   ```

2. Run it with the file:

   ```bash
   flutter run --dart-define-from-file=atunwa.json
   ```

The native setup from the guide is already done:

- **Android:** the Atunwa Maven repository, the SDK dependency, `minSdk = 24`, and R8 keep rules.
- **iOS:** the **MediaSDK** Swift package (1.0.6 or later) on the Runner target, and background
  audio. Add your Ad Manager app ID to `ios/Runner/Info.plist` as `GADApplicationIdentifier` before
  you ship; without one, the SDK uses Google's sample app ID and logs a warning.

For a walkthrough of how this app is built and what you could add to it, see
[its guide](../docs/flutter.md).
