# Android starter

`examples/android` is a Jetpack Compose app that shows both integration modes: one station with a
player API key, or all of a publisher's stations with a publisher SDK key. You pick the mode at build
time. Code is in `app/src/main/java/com/atunwadigital/examples/media/`.

- [Project setup](#project-setup)
- [Startup](#startup)
- [Single-player mode](#single-player-mode)
- [Publisher mode](#publisher-mode)
- [The SDK's views in Compose](#the-sdks-views-in-compose)
- [Observing playback](#observing-playback)
- [Possibilities](#possibilities)

---

## Project setup

| File | What it does |
|---|---|
| `settings.gradle.kts` | Adds the Atunwa Maven repository (`com.atunwadigital` and `com.adswizz` groups only) |
| `gradle/libs.versions.toml` | `atunwa-mediasdk` 1.0.8, plus `androidx.cardview` (see below) |
| `app/build.gradle.kts` | Reads `ATUNWA_APP_MODE`, `ATUNWA_PLAYER_KEY` and `ATUNWA_PUBLISHER_SDK_KEY` from `local.properties` into `BuildConfig`; `minSdk = 24`; R8 with the SDK's keep rules for release |
| `app/proguard-rules.pro` | Keeps the SDK's `kotlinx.serialization` models |
| `AndroidManifest.xml` | Nothing SDK-specific: the SDK's manifest merges in its service, player activity and permissions |

**CardView.** `SDKLiveCardView` extends `androidx.cardview.widget.CardView`, but the SDK publishes
CardView as a runtime-only dependency, so Kotlin code that references the view doesn't compile
without it. The app adds `androidx.cardview:cardview:1.0.0`. XML-only layouts don't need it.

`AtunwaConfig.kt` turns the build fields into one of three states: `SinglePlayer(playerApiKey)`,
`Publisher(publisherSdkKey)`, or `Missing(problem)`, which shows a screen explaining what to add.

## Startup

`MainActivity` runs `setup()` before showing any SDK view, then picks the screen:

```kotlin
LaunchedEffect(Unit) {
    MediaSDK.setup(applicationContext).onFailure { Log.e(TAG, "MediaSDK setup failed", it) }
    isSetUp = true
}

when {
    !isSetUp -> LoadingState()
    config is AtunwaConfig.SinglePlayer -> SinglePlayerScreen(config.playerApiKey)
    config is AtunwaConfig.Publisher -> PublisherScreen(config.publisherSdkKey)
    config is AtunwaConfig.Missing -> MissingKeyScreen(config.problem)
}
```

`setup()` binds the SDK's `MediaController` and starts the ad managers. It's idempotent, so running
it again after a configuration change returns straight away.

## Single-player mode

`single/SinglePlayerScreen.kt`. An app for one station.

**1. Fetch the station.** `initialize()` makes it the active station, so its name, logo and
publisher are available before anything plays:

```kotlin
MediaSDK.initialize(context.applicationContext, playerApiKey)
    .onSuccess { station = MediaSDK.playerConfig }
```

It's skipped when the station is already active (after rotation, say), because initializing again
would interrupt playback.

**2. Show it, then start it on demand.** Before anything plays, the screen shows its own station
card with **Listen live** and **Play here**:

```kotlin
private fun startStation(context: Context, openPlayer: Boolean) {
    if (MediaSDK.currentMediaItem == null) MediaSDK.load()
    if (openPlayer) MediaSDK.launchPlayer(context)
}
```

`load()` is needed because `initialize()` doesn't load the stream, and the SDK treats the
initialized station as active: `launchPlayer(context, apiKey)` or the live card's play button would
otherwise do nothing. Once a stream is loaded, the screen swaps its card for the SDK's
`SDKLiveCardView`, which plays and pauses in place.

**3. Recover after close.** The mini player's close button calls `reset()`, which clears the active
station, so the screen initializes again straight after (from the SDK's cache):

```kotlin
MiniPlayer(playback = playback, onClose = {
    MediaSDK.reset()
    scope.launch { initialize() }
})
```

> If you don't need the station's details before playback starts, one call replaces steps 1 and 2:
> `MediaSDK.launchPlayer(context, playerApiKey)`.

## Publisher mode

`publisher/PublisherScreen.kt`. An app offering every station a publisher has.

**1. Fetch the publisher.** `initializePublisher()` returns the publisher and its stations. It
uses the memory or disk cache when it has one, and doesn't start anything:

```kotlin
MediaSDK.initializePublisher(context.applicationContext, publisherSdkKey)
    .onSuccess { config = it }
```

**2. Show the stations.** The screen is a `LazyColumn` with:

- the SDK's live card, previewing the publisher's first station until something is active;
- a station count and a Radio/TV filter (from `PlayerConfig.streamChannels.isVideo`), shown only
  when the publisher has both;
- one row per station: logo, name, "Radio" or "TV", and a play/pause button.

**3. Two ways to play a station.**

| Action | Call | Result |
|---|---|---|
| Tap the row | `MediaSDK.loadPlayer(context, station)` | Switches station and opens the SDK's full-screen player |
| Tap the row's play button | `MediaSDK.activatePlayer(context, station)` | Switches station and plays in place; the mini player appears |

The active row is highlighted by comparing `station.assetKey` with the active station's asset key.

**4. Pull to refresh.** `refreshPublisherConfig()` always goes to the network, so a failure is
shown in a snackbar rather than hidden by the cache.

**5. Recover after close.** `reset()` also clears `publisherConfig`, which the full-screen player's
related-channels rail reads, so the screen calls `initializePublisher()` again after closing.

## The SDK's views in Compose

`ui/SdkViews.kt` wraps the SDK's Android Views with `AndroidView`. They observe the SDK on their
own; the wrappers only set callbacks.

```kotlin
@Composable
fun LiveCard(previewPlayer: PlayerConfig?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context -> SDKLiveCardView(context) },
        update = { card ->
            card.previewPlayer = previewPlayer
            card.onCardClickListener = onClick
        },
    )
}
```

`MiniPlayer` shows `SDKMiniPlayerView` only while a stream is loaded and the full-screen player isn't
open (it counts as open while in Picture-in-Picture), with `launchPlayer(context)` on expand.

## Observing playback

`ui/Playback.kt` collects four of the SDK's `StateFlow`s with `collectAsStateWithLifecycle`, so the
screens recompose when they change and stop collecting in the background:

| Flow | Used for |
|---|---|
| `MediaSDK.state` | Whether a stream is loaded (`hasStream`); playing vs. buffering |
| `MediaSDK.playWhenReady` | The play/pause icon |
| `MediaSDK.isDedicatedPlayerActive` | Hiding the mini player while the full-screen player or PiP is up |
| `MediaSDK.currentMediaItemFlow` | Re-reading the active station when it changes |

## Possibilities

Things this app could add with little code, each using the SDK as it is:

- **A TV entry point.** Filter `publisherConfig.players` for `streamChannels.isVideo` and open the
  first with `loadPlayer`. Video opens full screen in landscape; set
  `MediaSDK.videoFullscreenByDefault = false` to open it in portrait instead.
- **Keep Back in your app.** `MediaSDK.videoPipOnBack = false` stops Back from minimizing a playing
  video into Picture-in-Picture.
- **Brand the player.** Pass `SDKThemeConfig(primaryColorHex = …)` to `initialize` or
  `initializePublisher`. The live card also uses each station's own dashboard colors.
- **A waveform that follows the audio.** Request `RECORD_AUDIO` and `SDKWaveformView` switches from
  its synthetic animation to the real signal. Or remove the permission in your manifest if you'd
  rather not ask.
- **Your own player screen.** Use `SDKVideoView` with `MediaSDK.attachVideoView()` and drive your
  controls from the same flows. See
  [Building a custom player screen](../../android/docs/media-playback.md#building-a-custom-player-screen).
- **Prerolls tuned to your audience.** Set `MediaSDK.imaConfig` (language, timeouts) before the first
  ad-enabled load.

More in [What else you can build](possibilities.md).
