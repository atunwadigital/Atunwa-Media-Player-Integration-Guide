# Integration modes

The SDK supports two ways of identifying content. Both modes share the same playback engine, state
machine, UI components and ad pipeline. They differ only in how a station's `PlayerConfig` is
fetched and activated.

| | Single-player (Mode A) | Publisher (Mode B) |
|---|---|---|
| Use when | The app streams exactly one station | The app offers a publisher's list of stations |
| Credential | Player **API key** | Publisher **SDK key** |
| Fetches | One `PlayerConfig` | A `PublisherConfig` with `players: [PlayerConfig]` |
| Endpoint | `GET {base}/sdk/v1/player?key={apiKey}` | `GET {base}/sdk/v1/config?key={sdkKey}` |

Nothing forces you to choose a mode at build time. You can call whichever functions fit at runtime.

---

## Single-player mode (Mode A)

### One-call launch (recommended)

```swift
MediaSDK.shared.launchPlayer(
    apiKey: PLAYER_API_KEY,
    themeConfig: SDKThemeConfig(),   // optional, see theming.md
    autoplay: true,                  // optional
    from: self                       // optional; nil = top-most view controller
)
```

`launchPlayer(apiKey:themeConfig:autoplay:from:)`:

1. If `apiKey` is already the active station, brings `SDKPlayerViewController` to the front and
   does nothing else.
2. Otherwise presents `SDKPlayerViewController` right away. The screen shows its loading state.
3. In the background, calls `initialize(…)`, then `load()` if initialization succeeds.

Wire it to whatever starts playback in your UI:

```swift
let onPlay = { [weak self] in MediaSDK.shared.launchPlayer(apiKey: PLAYER_API_KEY, from: self) }
liveCard.onCardClickListener = onPlay
listenButton.addAction(UIAction { _ in onPlay() }, for: .touchUpInside)
```

### Warming the cache with `preload()`

```swift
Task { await MediaSDK.shared.preload(apiKey: PLAYER_API_KEY) }
```

`preload(apiKey:)` fetches the config into the in-memory cache, so a later `initialize` or
`launchPlayer` call doesn't wait on the network. It never throws (a failure is only logged), it
doesn't activate the station (`playerConfig` stays `nil`), and it doesn't write the on-disk cache.

### Step-by-step: `initialize()` + `load()`

Use this when you want to control fetching and playback start separately, for example to show your
own loading UI, or to start playback without opening the full-screen player:

```swift
Task {
    do {
        try await MediaSDK.shared.initialize(
            apiKey: PLAYER_API_KEY,
            themeConfig: SDKThemeConfig(primaryColorHex: "#0057FF"),
            autoplay: true
        )
        try MediaSDK.shared.load()                     // start the station's stream
        // MediaSDK.shared.launchPlayer(from: self)    // and/or open the built-in player
    } catch {
        showError(error)
    }
}
```

`initialize(apiKey:themeConfig:autoplay:)`:

- Moves the state to `.initializing`, then back to `.idle` on success or to `.error` on failure.
- Uses the in-memory cache if it has this key. Otherwise it fetches, retrying up to 3 times with
  1 s, 2 s and 4 s backoff. If every attempt fails, it falls back to the last config persisted on
  disk for this key (see [offline start](media-playback.md#offline-start--config-caching)).
- Sets `MediaSDK.shared.playerConfig` and applies branding to `SDKThemeManager`: the publisher tier,
  your `themeConfig` colors and the station logo.
- Stores `autoplay` as the default for later `load()` calls.

---

## Publisher mode (Mode B)

### Initialize

```swift
Task {
    do {
        let config = try await MediaSDK.shared.initializePublisher(sdkKey: PUBLISHER_SDK_KEY)
        config.publisher   // PublisherInfo: name, id, logo, tier, newsSources
        config.players     // [PlayerConfig]: every active station
    } catch {
        // non-fatal; retry later with refreshPublisherConfig(sdkKey:)
    }
}
```

`initializePublisher(sdkKey:themeConfig:)` caches the result in memory and on disk, and falls back
to the disk copy when it's offline. It sets `MediaSDK.shared.publisherConfig` and applies
publisher-level branding. Unlike `initialize`, it doesn't change the state, and it doesn't activate
a station.

### Activating a station

| Call | Switches the station | Presents `SDKPlayerViewController` | Use for |
|---|---|---|---|
| `MediaSDK.shared.loadPlayer(player, from: vc)` | ✅ | ✅ | A station picker, or any tap that should go to the player |
| `MediaSDK.shared.activatePlayer(player)` | ✅ | ❌ | In-place play buttons, such as a station-list row or a live card |

Both calls are no-ops when `player.assetKey` is already active. `loadPlayer` then just brings the
player forward, and `activatePlayer` calls `play()`. Otherwise they:

1. Send `dismissPlayerSignal`, and close any Picture-in-Picture window still showing the old station.
2. Set `MediaSDK.shared.playerConfig` and swap the station logo into the current theme, keeping all
   other theme values.
3. Call `load()`.

There's only ever one player screen. `loadPlayer` while it's already showing switches the station in
it; while it's minimized to PiP, it brings that same screen back.

### One-step launch

```swift
MediaSDK.shared.launchPublisher(sdkKey: PUBLISHER_SDK_KEY, from: self)
```

This initializes publisher mode. If the publisher has **exactly one** station, it goes straight to
`loadPlayer` for that station. With several stations, it only initializes. Read
`MediaSDK.shared.publisherConfig` and present your own picker.

### Full example: default station, quick-tune row and station picker

```swift
final class HomeViewController: UIViewController {
    private let liveCard = SDKLiveCardView()
    private var stations: [PlayerConfig] { MediaSDK.shared.publisherConfig?.players ?? [] }

    func setupPublisherMode() {
        let defaultPlayer = stations.first

        // Live card: show the default station's branding before anything has loaded.
        liveCard.previewPlayer = defaultPlayer
        liveCard.onCardClickListener = { [weak self] in
            guard let self else { return }
            if MediaSDK.shared.playerConfig != nil {
                MediaSDK.shared.launchPlayer(from: self)              // resume active
            } else if let defaultPlayer {
                MediaSDK.shared.loadPlayer(defaultPlayer, from: self)
            } else {
                openStationPicker()
            }
        }

        // A horizontal row with the first few stations.
        quickTune.stations = Array(stations.prefix(6))
        quickTune.onSelect = { [weak self] player in MediaSDK.shared.loadPlayer(player, from: self) }

        seeAllButton.setTitle("See all (\(stations.count)) ›", for: .normal)
    }

    func openStationPicker() {
        guard MediaSDK.shared.publisherConfig != nil else { return toast("Stations not loaded yet.") }
        let picker = StationPickerViewController(stations: stations) { [weak self] player in
            MediaSDK.shared.loadPlayer(player, from: self)
        }
        present(picker, animated: true)
    }
}
```

To highlight the active station in a list, compare against `MediaSDK.shared.playerConfig?.assetKey`,
and re-render whenever `currentMediaItemPublisher` emits.

### Filtering video and audio stations

Each `PlayerConfig.streamChannels` (`isAudio`, `isVideo`) tells you the station type. For example, a
"TV" entry point can open the first video station:

```swift
if let tv = MediaSDK.shared.publisherConfig?.players.first(where: { $0.streamChannels?.isVideo == true }) {
    MediaSDK.shared.loadPlayer(tv, from: self)
}
```

When a video station is active, the built-in player shows a rail of the publisher's other video
stations (in its portrait layout).

---

## Pull-to-refresh

Both modes have a refresh call that **always** goes to the network. It never falls back to the
in-memory or on-disk cache, so a failure is reported to you instead of being hidden:

```swift
@objc private func handleRefresh() {
    Task {
        do {
            let stations: [PlayerConfig]
            if isPublisherMode {
                stations = try await MediaSDK.shared.refreshPublisherConfig(sdkKey: PUBLISHER_SDK_KEY).players
            } else {
                stations = [try await MediaSDK.shared.refreshPlayerConfig(apiKey: PLAYER_API_KEY)]
            }
            render(stations)
        } catch {
            toast("Couldn't refresh")
        }
        refreshControl.endRefreshing()
    }
}
```

In SwiftUI, call the same functions from `.refreshable { … }`.

When it succeeds, the refresh updates the cache, the persisted copy, and `playerConfig` or
`publisherConfig`. `refreshPlayerConfig` replaces `playerConfig` but doesn't reload the stream
that's playing. Call `load()` if the stream URL might have changed.

## Stopping and switching back

`MediaSDK.shared.reset()` stops playback, clears the current item, **clears both `playerConfig` and
`publisherConfig`**, returns to `.idle`, closes the built-in player (and any PiP window) and sends
`dismissPlayerSignal`. After a reset, initialize again with `initialize`/`initializePublisher`, or use
one of the `launch…` calls, before you play anything. The in-memory config caches survive the reset,
so initializing again doesn't need the network.

> In publisher mode, `reset()` also drops `publisherConfig`. If your home screen reads the station
> list from it, call `initializePublisher(sdkKey:)` again. It returns immediately from the cache.
