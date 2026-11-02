# React Native example

A React Native app (Android and iOS) using the Atunwa Media SDK through the native module from the
[React Native guide](../../react-native/README.md). See the [examples overview](../README.md) for
what the two modes show.

| File | What it shows |
|---|---|
| [`src/atunwaMedia.tsx`](src/atunwaMedia.tsx) | The JavaScript API, the `usePlayback` hook, and the live card and mini player components |
| [`src/SinglePlayerScreen.tsx`](src/SinglePlayerScreen.tsx) | Single-player mode, with a player API key |
| [`src/PublisherScreen.tsx`](src/PublisherScreen.tsx) | Publisher mode, with a publisher SDK key |
| [`android/app/src/main/java/…/atunwa/`](android/app/src/main/java/com/atunwadigital/examples/rn/atunwa) | The Android module and view managers |
| [`ios/AtunwaExample/AtunwaMedia.swift`](ios/AtunwaExample/AtunwaMedia.swift), `AtunwaViewManagers.swift` | The iOS module and view managers |

The module here extends the guide's module with what the screens need:

- `initializePlayer()` resolves with the station.
- `activeStation()`, `load()` and `refreshPublisher()` are new.
- `initializePublisher()` resolves with the publisher as well as its stations.
- The `AtunwaMediaPlayback` event reports the active station and whether the full-screen player is
  open, not just the state.

It uses React Native's classic module API, which React Native 0.88 loads through its interop layer.

## Run it

1. Install. This also creates `atunwa.config.json` (git-ignored) from the example:

   ```bash
   npm install
   cd ios && pod install && cd ..
   ```

2. Set the mode and your key in `atunwa.config.json`:

   ```json
   {
     "ATUNWA_APP_MODE": "publisher",
     "ATUNWA_PLAYER_KEY": "",
     "ATUNWA_PUBLISHER_SDK_KEY": "…"
   }
   ```

3. Run it:

   ```bash
   npm run android
   npm run ios
   ```

The native setup from the guide is already done:

- **Android:** the Atunwa Maven repository, the SDK dependency, and R8 keep rules.
- **iOS:** the **MediaSDK** Swift package (1.0.6 or later) on the app target, background audio,
  and landscape for full-screen video. Add your Ad Manager app ID to `ios/AtunwaExample/Info.plist`
  as `GADApplicationIdentifier` before you ship; without one, the SDK uses Google's sample app ID
  and logs a warning.

The SDK is distributed through Swift Package Manager, so it's added to the Xcode project, not the
`Podfile`. Using Expo? See the [Expo guide](../../react-native/docs/expo.md).

For a walkthrough of how this app is built and what you could add to it, see
[its guide](../docs/react-native.md).
