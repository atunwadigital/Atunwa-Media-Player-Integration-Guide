# React Native starter

`examples/react-native` is a React Native 0.88 app (New Architecture) for Android and iOS. The UI is
TypeScript; playback, the player screens and ads stay native, behind a native module the app owns.
It's the module from the [React Native guide](../../react-native/README.md), extended with what the
screens need.

- [How the pieces fit](#how-the-pieces-fit)
- [Native setup](#native-setup)
- [The module](#the-module)
- [Screens](#screens)
- [Native views](#native-views)
- [Possibilities](#possibilities)

---

## How the pieces fit

```
src/SinglePlayerScreen.tsx ┐
src/PublisherScreen.tsx    ├─▶ src/atunwaMedia.tsx ──NativeModules.AtunwaMedia──────────────┐
src/App.tsx                ┘   (AtunwaMedia, usePlayback, views) ──"AtunwaMediaPlayback" event┤
                                                                                              ▼
       Android: android/app/src/main/java/…/atunwa/  AtunwaMediaModule, AtunwaViewManagers  ──▶ MediaSDK
       iOS:     ios/AtunwaExample/  AtunwaMedia.swift + .m, AtunwaViewManagers.swift + .m     ──▶ MediaSDK
```

The mode and key come from `atunwa.config.json`. `npm install` creates it from
`atunwa.config.example.json` (a `postinstall` script), and `src/config.ts` imports it.

## Native setup

| Platform | What was added |
|---|---|
| Android | An `allprojects` block in `android/build.gradle` with the Atunwa Maven repository; `mediasdk`, `kotlinx-coroutines-android` and `androidx.cardview` in `android/app/build.gradle`; R8 keep rules |
| Android | `AtunwaMediaPackage()` added to the package list in `MainApplication.kt` |
| iOS | The **MediaSDK** Swift package on the **AtunwaExample** target (1.0.6 or later). It's distributed through Swift Package Manager only, so it's added to the Xcode project, alongside the CocoaPods the template uses |
| iOS | A bridging header importing `RCTBridgeModule.h`, `RCTEventEmitter.h` and `RCTViewManager.h`; background audio and landscape orientations in `Info.plist` |

No `GADApplicationIdentifier` is set on iOS; SDK 1.0.6+ falls back to Google's sample app ID and logs
a warning. Add yours to `ios/AtunwaExample/Info.plist` before you ship.

The module uses React Native's classic module API (`ReactContextBaseJavaModule`, `RCT_EXTERN_MODULE`).
React Native 0.88 runs only the New Architecture and loads modules written this way through its
interop layer; it was verified running on both platforms.

## The module

`src/atunwaMedia.tsx` is the only file the screens import:

| JavaScript | Android | iOS |
|---|---|---|
| `setup()` | `MediaSDK.setup(context)` | First access to `MediaSDK.shared` |
| `initializePlayer(apiKey)` → station | `initialize(context, apiKey)` | `initialize(apiKey:)` |
| `activeStation()` → station or `null` | `playerConfig` | `playerConfig` |
| `load()` | `load()`, unless something is loaded | `load()`, unless something is loaded |
| `openPlayer()`, `launchPlayer(apiKey)` | `launchPlayer(activity)`, `launchPlayer(activity, apiKey)` | `launchPlayer()`, `launchPlayer(apiKey:)` |
| `initializePublisher(sdkKey)`, `refreshPublisher(sdkKey)` → publisher | `initializePublisher`, `refreshPublisherConfig` | Same |
| `loadStation(assetKey)`, `activateStation(assetKey)` | `loadPlayer`, `activatePlayer` | Same |
| `play()`, `pause()`, `reset()` | Same | Same |
| `usePlayback()` hook | `state`, `playWhenReady`, `currentMediaItemFlow`, `isDedicatedPlayerActive` | The four matching publishers |

It's called `initializePlayer`, not `initialize`, because React Native modules already have an
`initialize` method on both platforms.

**Promises and errors.** Every method returns a promise. Failures reject with a `code` naming the
call (`initializePublisher`, `load`, …), or `unknownStation` when a station's asset key isn't in the
loaded publisher config, as after `reset()`. Calls that open the player reject with `noActivity` if
Android has no foreground activity.

**Events.** The module emits `AtunwaMediaPlayback` with `{state, playWhenReady, activeAssetKey,
isPlayerOpen}`, only while JavaScript is listening (`addListener` / `removeListeners` start and stop
the native subscription). `usePlayback()` wraps it:

```tsx
export function usePlayback(): AtunwaPlayback {
  const [playback, setPlayback] = useState(idle);
  useEffect(() => AtunwaMedia.addPlaybackListener(setPlayback), []);
  return playback;
}
```

`NativeEventEmitter.addListener` types its listener as taking `Object`s, so the module casts the event
to `AtunwaPlayback` rather than passing a typed listener straight in, which TypeScript rejects.

## Screens

**`SinglePlayerScreen.tsx`.** Shows the station from `initializePlayer`, then starts it with `load()`
and `openPlayer()`. Before starting, it calls `initializePlayer` again if `activeStation()` is `null`
(the mini player's close button reset the SDK).

**`PublisherScreen.tsx`.** A `FlatList` with the live card and filter chips as its header, and a
`StationRow` per station: a tap calls `loadStation`, the play button calls `activateStation` (or
`pause()` / `play()` for the station that's playing). `RefreshControl` calls `refreshPublisher`.
Station calls go through `withPublisher`, which catches `unknownStation`, initializes again and
retries once.

`components.tsx` holds the layout: a title bar, the mini player pinned to the bottom with
`react-native-safe-area-context` insets, light and dark colors, and the messages for errors and a
missing key.

## Native views

| Component | Native | Size |
|---|---|---|
| `AtunwaLiveCard` | `SDKLiveCardView` | `width: 100%`, `aspectRatio: 16 / 9` |
| `AtunwaMiniPlayer` | `SDKMiniPlayerView` | 62 high on iOS, 76 on Android (the Android bar includes shadow margins) |

On Android each view is wrapped in a `RelayoutingFrame`, because React Native doesn't re-run Android
layout when a view changes its own children, which the SDK's views do as ads and video come and go.
On iOS the view managers return the SDK views directly.

The live card reads `publisherConfig` when it's created, so the publisher screen renders it after
`initializePublisher` resolves.

## Possibilities

- **Expo.** Run `npx expo prebuild` once, commit `ios/` and `android/`, and follow this app's native
  setup; don't use `prebuild --clean` afterwards. See the [Expo guide](../../react-native/docs/expo.md).
- **A news screen.** Add `fetchNews` to both modules and render the articles with `FlatList`.
- **On-demand content.** Add `loadItem({id, url, title, show, isVideo})` to build a `MediaItem` and
  play your own videos and podcasts (see [Media House](media-house.md)).
- **Ad-aware controls.** Emit `adPlaybackState` and `adCountdown` as a second event and show the ad
  countdown in your own UI.
- **A shared package.** Move the module into an npm package with its own `android/` and `ios/` code.
  On iOS it needs the SDK from Swift Package Manager, not a podspec dependency.

More in [What else you can build](possibilities.md).
