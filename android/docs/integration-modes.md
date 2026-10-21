# Integration modes

The SDK supports two ways of identifying content. Both modes share the same playback engine, state
machine, UI components and ad pipeline. They differ only in how a station's `PlayerConfig` is
fetched and activated.

| | Single-player (Mode A) | Publisher (Mode B) |
|---|---|---|
| Use when | The app streams exactly one station | The app offers a publisher's list of stations |
| Credential | Player **API key** | Publisher **SDK key** |
| Fetches | One `PlayerConfig` | A `PublisherConfig` with `players: List<PlayerConfig>` |
| Endpoint | `GET {base}/sdk/v1/player?key={apiKey}` | `GET {base}/sdk/v1/config?key={sdkKey}` |

Nothing forces you to choose a mode at build time. You can call whichever functions fit at runtime.

All examples assume `MediaSDK.setup(applicationContext)` has already run. See
[Getting started](getting-started.md#41-setup-always-first).

---

## Single-player mode (Mode A)

### One-call launch (recommended)

```kotlin
MediaSDK.launchPlayer(
    context = this,
    apiKey = PLAYER_API_KEY,
    themeConfig = SDKThemeConfig(),   // optional, see theming.md
    autoplay = true,                  // optional
)
```

`launchPlayer(context, apiKey, …)`:

1. If `apiKey` is already the active station, brings `SDKPlayerActivity` to the front and does
   nothing else.
2. Otherwise emits `dismissPlayerSignal`, which closes any PiP window still showing the previous
   station, and opens `SDKPlayerActivity` right away. The screen shows its loading state.
3. In the background, calls `initialize(…)`, then `load()` if initialization succeeds.

Wire it to whatever starts playback in your UI:

```kotlin
val onPlay = { MediaSDK.launchPlayer(this, PLAYER_API_KEY) }
findViewById<SDKLiveCardView>(R.id.liveCard).onCardClickListener = onPlay
findViewById<View>(R.id.listenButton).setOnClickListener { onPlay() }
```

### Warming the cache with `preload()`

```kotlin
lifecycleScope.launch { MediaSDK.preload(PLAYER_API_KEY) }
```

`preload()` fetches the config into the in-memory cache, so the later `initialize()` or
`launchPlayer()` call doesn't wait on the network. It doesn't activate the station:
`MediaSDK.playerConfig` stays `null` until `initialize()` runs. It also doesn't write the on-disk
cache. If a station's branding needs to be visible before playback starts, use `initialize()`.

### Step-by-step: `initialize()` + `load()`

Use this when you want to control fetching and playback start separately, for example to show
your own loading UI, or to start playback without opening the full-screen player:

```kotlin
lifecycleScope.launch {
    val result = MediaSDK.initialize(
        context = applicationContext,
        apiKey = PLAYER_API_KEY,
        themeConfig = SDKThemeConfig(primaryColorHex = "#0057FF"),
        autoplay = true,
    )
    result
        .onSuccess {
            MediaSDK.load()                          // start the station's stream
            // MediaSDK.launchPlayer(this@MyActivity) // and/or open the built-in player
        }
        .onFailure { showError(it) }
}
```

`initialize()`:

- Moves `state` to `INITIALIZING`, then back to `IDLE` on success or to `ERROR` on failure.
- Uses the in-memory cache if it has this key. Otherwise it fetches, retrying up to 3 times with
  1s, 2s and 4s backoff. If every attempt fails, it falls back to the last config persisted on
  disk for this key (see [offline start](media-playback.md#offline-start--config-caching)).
- Sets `MediaSDK.playerConfig` and applies branding to `SDKThemeManager`: the publisher tier, your
  `themeConfig` colors and the station logo.
- Stores `autoplay` as the default for later `load()` calls.

---

## Publisher mode (Mode B)

### Initialize

```kotlin
lifecycleScope.launch {
    MediaSDK.initializePublisher(applicationContext, PUBLISHER_SDK_KEY)
        .onSuccess { config ->
            config.publisher   // PublisherInfo: id, name, logo, loadingAnimation, website, newsSources
            config.players     // List<PlayerConfig>: every active station
        }
        .onFailure { /* non-fatal; retry later with refreshPublisherConfig() */ }
}
```

`initializePublisher()` caches the result in memory and on disk, and falls back to the disk copy
when it's offline. It sets `MediaSDK.publisherConfig` and applies publisher-level branding. Unlike
`initialize()`, it doesn't change `state`, and it doesn't activate a station.

### Activating a station

| Call | Switches the station | Opens `SDKPlayerActivity` | Use for |
|---|---|---|---|
| `MediaSDK.loadPlayer(context, player)` | ✅ | ✅ | A station picker, or any tap that should go to the player |
| `MediaSDK.activatePlayer(context, player)` | ✅ | ❌ | In-place play buttons, such as a station-list row or a live card |

Both calls are no-ops when `player.assetKey` is already active. `loadPlayer` then just brings the
UI to the front, and `activatePlayer` calls `play()`. Otherwise they:

1. Emit `dismissPlayerSignal`, which closes any PiP window for the old station.
2. Set `MediaSDK.playerConfig` and swap the station logo into the current theme, keeping all other
   theme values.
3. Call `setup()` (a no-op if it's already done), then `load()`.

### One-step launch

```kotlin
MediaSDK.launchPublisher(context, PUBLISHER_SDK_KEY)
```

This initializes publisher mode. If the publisher has **exactly one** station, it goes straight to
`loadPlayer` for that station. With several stations, it only initializes. Read
`MediaSDK.publisherConfig` and present your own picker.

### Full example: default station, quick-tune row and station picker

This is adapted from the sample app's `MainActivity`:

```kotlin
private fun setupPublisherMode() {
    val players = MediaSDK.publisherConfig?.players.orEmpty()
    val defaultPlayer = players.firstOrNull()

    // Live card: show the default station's branding before anything has loaded.
    val liveCard = findViewById<SDKLiveCardView>(R.id.liveCard)
    liveCard.previewPlayer = defaultPlayer
    liveCard.onCardClickListener = {
        when {
            MediaSDK.playerConfig != null -> MediaSDK.launchPlayer(this)          // resume active
            defaultPlayer != null        -> MediaSDK.loadPlayer(this, defaultPlayer)
            else                         -> openStationPicker()
        }
    }

    // A horizontal row with the first few stations.
    quickTuneAdapter = QuickTuneAdapter { player -> MediaSDK.loadPlayer(this, player) }
    quickTuneList.adapter = quickTuneAdapter
    quickTuneAdapter.submitList(players.take(6))

    seeAllButton.text = "See all (${players.size}) ›"
    seeAllButton.setOnClickListener { openStationPicker() }
}

private fun openStationPicker() {
    val config = MediaSDK.publisherConfig ?: return toast("Stations not loaded yet.")
    StationPickerSheet(config.players) { player -> MediaSDK.loadPlayer(this, player) }
        .show(supportFragmentManager, "stations")
}
```

To highlight the active station in a list, compare against `MediaSDK.playerConfig?.assetKey`, and
re-render whenever `MediaSDK.currentMediaItemFlow` emits.

### Filtering video and audio stations

Each `PlayerConfig.streamChannels` (`isAudio`, `isVideo`) tells you the station type. For example,
a "TV" entry point can open the first video station:

```kotlin
val tv = MediaSDK.publisherConfig?.players?.firstOrNull { it.streamChannels?.isVideo == true }
tv?.let { MediaSDK.loadPlayer(this, it) }
```

When a video station is active, the built-in player shows a rail of the publisher's other video
stations.

---

## Pull-to-refresh

Both modes have a refresh call that **always** goes to the network. It never falls back to the
in-memory or on-disk cache, so a failure is reported to you instead of being hidden:

```kotlin
swipeRefresh.setOnRefreshListener {
    lifecycleScope.launch {
        val result = if (isPublisherMode) {
            MediaSDK.refreshPublisherConfig(this@MainActivity, PUBLISHER_SDK_KEY)
                .map { it.players }
        } else {
            MediaSDK.refreshPlayerConfig(this@MainActivity, PLAYER_API_KEY)
                .map { listOf(it) }
        }
        result.onSuccess { renderStations(it) }.onFailure { toast("Couldn't refresh") }
        swipeRefresh.isRefreshing = false
    }
}
```

When it succeeds, the refresh updates the cache, the persisted copy, and `MediaSDK.playerConfig`
or `MediaSDK.publisherConfig`. `refreshPlayerConfig` replaces `playerConfig` but doesn't reload the
stream that's playing. Call `load()` if the stream URL might have changed.

## Stopping and switching back

`MediaSDK.reset()` stops playback, clears the current item, **clears both `playerConfig` and
`publisherConfig`**, and returns to `IDLE`. After a reset, initialize again with
`initialize`/`initializePublisher`, or use one of the `launch*` calls, before you play anything.
The in-memory config caches survive the reset, so initializing again doesn't need the network.

> In publisher mode, `reset()` also drops `publisherConfig`. If your home screen reads the station
> list from it, call `initializePublisher()` again. It returns immediately from the cache.
