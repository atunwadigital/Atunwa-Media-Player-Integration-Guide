# Troubleshooting React Native apps

Problems specific to using the SDK from React Native. For everything else, see the native guides
([iOS](../../ios/docs/troubleshooting.md), [Android](../../android/docs/troubleshooting.md)): the
SDK behaves the same inside a React Native app.

## Build and setup

| Symptom | Cause and fix |
|---|---|
| `NativeModules.AtunwaMedia` is `undefined`, or `new NativeEventEmitter()` requires a non-null argument | The module isn't registered, or the app is running an old native build. On Android, check `AtunwaMediaPackage` is added in `MainApplication.kt`; on iOS, check `AtunwaMedia.m` and `AtunwaMedia.swift` are in the app target. Then rebuild the app: Metro's reload never picks up native changes |
| Running in Expo Go | Expo Go can't load native code. See [Expo](expo.md) |
| Android: `Could not find com.atunwadigital:mediasdk` | The Atunwa Maven repository is missing from `allprojects.repositories` in `android/build.gradle`. See [Getting started → Add the repository](getting-started.md#11-add-the-repository) |
| Android: release build crashes on startup, debug works | R8 removed the SDK's config models. Add the keep rules from [Android Getting started → Code shrinking](../../android/docs/getting-started.md#6-code-shrinking) |
| iOS: `No such module 'MediaSDK'` | The package was added to the wrong target, or you opened the `.xcodeproj` instead of the `.xcworkspace`. Select **MyApp → General → Frameworks, Libraries, and Embedded Content** and check **MediaSDK** is listed |
| iOS: `Cannot find type 'RCTEventEmitter' in scope` | The bridging header doesn't import React's headers, or **Build Settings → Objective-C Bridging Header** doesn't point to it |
| iOS: the package disappears after `pod install` | It shouldn't: `pod install` only changes the Pods project. If a script regenerates your Xcode project (Expo's `prebuild --clean`, for example), add the package again. See [Expo](expo.md) |
| Duplicate symbols or `Class GAD… is implemented in both` | Your app also uses a library that bundles Google's ad SDKs, such as `react-native-google-mobile-ads` or an IMA wrapper. Two copies of the Google SDKs end up in the app. If you can, let the Atunwa SDK handle the ads it supports and drop the library. Otherwise, contact your Atunwa account manager |

## Playback

| Symptom | Cause and fix |
|---|---|
| A call rejects with `noActivity` (Android) | It was made while no Activity was in the foreground, for example from a background task. Call `launchPlayer`, `openPlayer` and `loadStation` from the UI |
| `loadStation` rejects with `unknownStation` | `initializePublisher` hasn't finished, or the key isn't in the current publisher's station list. Await `initializePublisher` first |
| `useAtunwaState()` stays `'idle'` | Nothing has been activated yet, or the event name doesn't match. It must be `AtunwaMediaState` in `atunwaMedia.ts` and in both native modules |
| iOS: playback stops in the background | The **Audio, AirPlay, and Picture in Picture** background mode is missing on the app target |
| Audio stops when another part of the app plays sound | Another audio library (`react-native-track-player`, `react-native-video`, `expo-av`, `expo-audio`) took over the audio session or media session. Pause the SDK with `AtunwaMedia.pause()` before playing other audio, and don't use `react-native-track-player` for the SDK's streams: the SDK already provides lock-screen and notification controls |
| Two sets of lock-screen controls or media notifications | Same cause: another library also registered a media session. Only one should be active at a time |

## Native views

| Symptom | Cause and fix |
|---|---|
| `Invariant Violation: requireNativeComponent: "AtunwaLiveCard" was not found in the UIManager` | The view managers aren't registered. Check `createViewManagers` on Android and `AtunwaViewManagers.m` on iOS, then rebuild |
| The live card or mini player has zero size | React Native sizes native views from their style. Keep the `width`, `aspectRatio` and `height` from [Native views](native-views.md#javascript) |
| Android: part of a view stays blank or stale after an ad or a station change | The view isn't wrapped in `RelayoutingFrame`, so React Native skipped its layout pass. See [Native views → Android](native-views.md#android) |
| The card shows nothing before playback | There's no `previewPlayer`. Call `initializePublisher` before the card is rendered |

## Logs

The SDK's log lines start with `MediaSDK`, on both platforms. Read them with
`adb logcat | grep MediaSDK` on Android and in Xcode's console on iOS (`console.log` output in
Metro doesn't include them), and include them when you report a problem.
