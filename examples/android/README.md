# Android example

A Jetpack Compose app using the Atunwa Media SDK for Android. See the [examples overview](../README.md)
for what the two modes show.

| File | What it shows |
|---|---|
| [`MainActivity.kt`](app/src/main/java/com/atunwadigital/examples/media/MainActivity.kt) | `MediaSDK.setup()`, then the screen for the configured mode |
| [`single/SinglePlayerScreen.kt`](app/src/main/java/com/atunwadigital/examples/media/single/SinglePlayerScreen.kt) | Single-player mode, with a player API key |
| [`publisher/PublisherScreen.kt`](app/src/main/java/com/atunwadigital/examples/media/publisher/PublisherScreen.kt) | Publisher mode, with a publisher SDK key |
| [`ui/SdkViews.kt`](app/src/main/java/com/atunwadigital/examples/media/ui/SdkViews.kt) | The SDK's live card and mini player in Compose |
| [`ui/Playback.kt`](app/src/main/java/com/atunwadigital/examples/media/ui/Playback.kt) | Observing the SDK's state flows |

## Run it

1. Copy `local.properties.example` to `local.properties` (Android Studio adds `sdk.dir` to it).
2. Set the mode and your key:

   ```properties
   ATUNWA_APP_MODE=single          # or publisher
   ATUNWA_PLAYER_KEY=…             # single
   ATUNWA_PUBLISHER_SDK_KEY=…      # publisher
   ```

3. Open this folder in Android Studio and run the `app` configuration, or run
   `./gradlew installDebug`.

The keys are compiled into `BuildConfig`, so rebuild after you change them.

Requires Android 7.0 (API 24) or later. The SDK comes from this repository's Maven repository; see
`settings.gradle.kts`.

For a walkthrough of how this app is built and what you could add to it, see
[its guide](../docs/android.md).
