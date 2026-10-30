# Troubleshooting

## Build & setup

| Symptom | Cause and fix |
|---|---|
| Package resolution asks for credentials or fails with an authentication error | The package needs none. Check the package URL is `https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git` (the SDK's source repository is private and isn't for installing) |
| `Compiled module was created by a newer version of the compiler` or `cannot load module 'MediaSDK'` | The prebuilt SDK was built with a newer Xcode than yours. Update Xcode, or use an older SDK version |
| `No such module 'UIKit'` in the editor while the iOS build succeeds | The package also declares macOS, and the editor is indexing for it. Pick an iOS destination; the SDK's UI is iOS-only |
| Swift 6 concurrency errors calling the SDK from a background task | `MediaSDK` is `@MainActor`. Call it from the main actor (`await MainActor.run { … }` or a `@MainActor` function) |

## Playback

| Symptom | Cause and fix |
|---|---|
| Playback stops when the app goes to the background or the screen locks | The `audio` background mode is missing. Add **Audio, AirPlay, and Picture in Picture** under **Background Modes** |
| `play()` does nothing | The SDK isn't initialized yet. Call `initialize`, `initializePublisher` or a station switch first. Check `MediaSDK.shared.isInitialized` |
| `load()` throws `notInitialized` | No active `playerConfig`. Initialize first, or activate a station |
| State goes to `.error` after a while on a weak network | Three retries and stall recovery all failed. Log line: `MediaSDK: playback failed, retry …`. `play()` retries again |
| `MediaSDK: Invalid state transition: …` in the console | A transition the state machine doesn't allow was attempted and ignored. Harmless on its own, for example `buffering → loading` while a new station loads |
| Video stays portrait instead of going full screen | The app doesn't support the landscape orientations, so the rotation request is refused. Log line: `MediaSDK: player rotation request failed — …`. Add them under **Device Orientation**, or set `videoFullscreenByDefault = false` |
| Turning the phone doesn't switch full screen | Rotation lock is on (this is deliberate), or the player has just opened and the phone hasn't yet been held the way the current mode expects |
| Closing a playing video just closes instead of going to PiP | PiP isn't supported on the device or in the Simulator, the `audio` background mode is missing, the video isn't playing, or `videoPipOnBack` is `false`. Log line: `MediaSDK: PiP — isPictureInPictureSupported() is false …` |
| `togglePiP()` does nothing | PiP was never set up on a layer that's on screen and playing. Use `createVideoView()`/`attachVideoView(_:)`. Log lines start with `MediaSDK: PiP —` |
| Offline launch shows no stations | Nothing was ever cached for this key. The disk fallback only has data after one successful online fetch |

## Ads

| Symptom | Cause and fix |
|---|---|
| No banners at all | `GADApplicationIdentifier` is missing, empty or not in `ca-app-pub-<digits>~<digits>` form (an unresolved `$(VAR)` counts). Log line: `MediaSDK: SDKGptManager — no valid Ad Manager app ID (GADApplicationIdentifier) in Info.plist, GPT banners disabled` |
| Banner never shows on a station with a preroll | The banner waits for the preroll to resolve, up to 12 s. Also check `onContentStateChanged`: a load can fail or not fill |
| Banner missing while video is full screen | Deliberate: nothing is requested into a hidden spot. It loads when the viewer leaves full screen |
| No preroll | The station has no `imaUrl` (`PlayerConfig.adUrl`), or no real screen has attached the ad surface yet. A host-built player must call `SDKAdDisplayContainer.shared.attachAdSurface(to:viewController:)`. Log lines start with `MediaSDK: IMAAdProvider` |
| Crash mentioning "child view controller … should have parent view controller" | The IMA ad view was moved to another view controller mid-ad. Don't reparent `adContainerView` yourself; use `attachAdSurface`, which refuses moves during an ad |
| No AdsWizz companions | The stream isn't live, the ad break has no companion for zone 2459, or the companion view is hidden. Log lines start with `MediaSDK: AdsWizz` |

## Log lines

The SDK logs with `print`, every line prefixed `MediaSDK:`. In Xcode's console, filter on
`MediaSDK:`; for a device, use Console.app filtered on your app's process.

| Prefix | Area |
|---|---|
| `MediaSDK: Loading stream` | Item loads |
| `MediaSDK: playback …` | Retries and stall recovery |
| `MediaSDK: Invalid state transition` | State machine |
| `MediaSDK: PiP —` | Picture-in-Picture setup and start |
| `MediaSDK: player rotation request failed` | Full-screen rotation |
| `MediaSDK: IMAAdProvider` | IMA |
| `MediaSDK: SDKGptManager` | Google Mobile Ads startup |
| `MediaSDK: AdsWizz` | AdsWizz initialization and SSI |
