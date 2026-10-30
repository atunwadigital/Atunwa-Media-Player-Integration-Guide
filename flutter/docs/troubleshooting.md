# Troubleshooting Flutter apps

Problems specific to using the SDK from Flutter. For everything else, see the native guides
([iOS](../../ios/docs/troubleshooting.md), [Android](../../android/docs/troubleshooting.md)): the
SDK behaves the same inside a Flutter app.

## Build and setup

| Symptom | Cause and fix |
|---|---|
| `MissingPluginException(No implementation found for method … on channel atunwa/media)` | The bridge isn't registered, or you hot-reloaded after adding it. Check `MainActivity` / `AppDelegate` create it, then stop the app and rebuild. Hot reload and hot restart never pick up native changes |
| Android: `Could not find com.atunwadigital:mediasdk` | The Atunwa Maven repository isn't in `allprojects.repositories` in `android/build.gradle.kts`. See [Getting started → Add the repository](getting-started.md#11-add-the-repository) |
| Android: `Build was configured to prefer settings repositories over project repositories` | `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`. Remove it; Flutter plugins need project repositories |
| Android: `uses-sdk:minSdkVersion 21 cannot be smaller than version 24` | Set `minSdk = 24` in `android/app/build.gradle.kts` |
| Android: release build crashes on startup, debug works | R8 removed the SDK's config models. Add the keep rules from [Android Getting started → Code shrinking](../../android/docs/getting-started.md#6-code-shrinking) |
| iOS: `No such module 'MediaSDK'` | The package was added to the wrong target. In Xcode, select **Runner → General → Frameworks, Libraries, and Embedded Content** and check **MediaSDK** is listed |
| iOS: `Compiling for iOS 13.0, but module 'MediaSDK' has a minimum deployment target of iOS 15.0` | Raise the Runner target and the `Podfile` platform to iOS 15.0 |
| Duplicate symbols or `Class GAD… is implemented in both` | Your app also uses a Flutter plugin that bundles Google's ad SDKs, such as `google_mobile_ads` or `interactive_media_ads`. Two copies of the Google SDKs end up in the app. If you can, let the Atunwa SDK handle the ads it supports and drop the plugin. Otherwise, contact your Atunwa account manager |

## Playback

| Symptom | Cause and fix |
|---|---|
| `loadStation` fails with `unknownStation` | `initializePublisher` hasn't finished, or the key isn't in the current publisher's station list. Await `initializePublisher` first |
| iOS: playback stops in the background | The **Audio, AirPlay, and Picture in Picture** background mode is missing on the Runner target |
| Audio stops when another part of the app plays sound | Another audio plugin (`just_audio`, `audioplayers`, `audio_service`, `video_player`) took over the audio session or media session. Pause the SDK with `AtunwaMedia.pause()` before playing other audio, and don't use `audio_service` for the SDK's streams: the SDK already provides lock-screen and notification controls |
| Two sets of lock-screen controls or media notifications | Same cause: another plugin also registered a media session. Only one should be active at a time |

## Native views

| Symptom | Cause and fix |
|---|---|
| The live card or mini player is blank | The view factory isn't registered under the same name the Dart widget uses (`atunwa/live_card`, `atunwa/mini_player`), or nothing is active and there's no `previewPlayer`. Call `initializePublisher` before the card is built |
| The live card is cropped or letterboxed | It's always 16:9. Keep it inside `AspectRatio(aspectRatio: 16 / 9)` |
| Scrolling stutters on Android around a native view | Platform views are expensive in scrolling content. Keep SDK views out of lists, and see Flutter's notes on [Android platform views](https://docs.flutter.dev/platform-integration/android/platform-views) |

## Logs

The SDK's log lines start with `MediaSDK`, on both platforms. `flutter run` shows Android's
`logcat` output and iOS's console output in the same terminal; filter on `MediaSDK` and include
those lines when you report a problem.
