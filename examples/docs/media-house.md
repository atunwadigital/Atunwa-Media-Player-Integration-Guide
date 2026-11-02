# Media House

`examples/media-house` is a full publisher app for **Android** (Jetpack Compose) and **iOS**
(SwiftUI): the way a radio, TV and news group might structure its own app around the SDK. One
**publisher SDK key** drives everything live; a bundled catalogue stands in for the publisher's own
on-demand content.

- [The app at a glance](#the-app-at-a-glance)
- [Architecture](#architecture)
- [Live content from one key](#live-content-from-one-key)
- [Four ways to play](#four-ways-to-play)
- [One mini player for the whole app](#one-mini-player-for-the-whole-app)
- [News and in-feed ads](#news-and-in-feed-ads)
- [Brand colors in the SDK's screens](#brand-colors-in-the-sdks-screens)
- [Edge cases it handles](#edge-cases-it-handles)
- [Bringing your own content](#bringing-your-own-content)
- [Possibilities](#possibilities)

---

## The app at a glance

Five tabs, with the SDK's mini player above the tab bar on every one:

| Tab | What's on it |
|---|---|
| **Home** | A greeting and the publisher's logo; the SDK's live card; "On air now" (radio stations as round logos); "Live TV" (16:9 channel tiles); "Top stories" (three headlines); "Catch up" (videos); "Podcasts" (shows) |
| **Radio** | Every audio station as a tile with a play button; the one playing shows an equalizer |
| **TV** | The live channels as large tiles, then a "Catch up" list of videos |
| **News** | A lead story with a large image, then headlines with thumbnails, and an Ad Manager banner after the third |
| **Podcasts** | Shows, each opening a list of episodes |

Every "See all" link on Home switches to the matching tab, and tapping a podcast on Home opens that
show's episodes in the Podcasts tab.

## Architecture

The same structure on both platforms, so a reader can map one onto the other:

| Concern | Android | iOS |
|---|---|---|
| Content and every SDK call | `data/MediaHouseViewModel.kt` (`AndroidViewModel`) | `AppModel.swift` (`ObservableObject`) |
| Playback state for the UI | `playback/Playback.kt` | `Playback` in `AppModel.swift` |
| Demo catalogue → `MediaItem` | `data/Catalogue.kt`, `assets/catalogue.json` | `Catalogue.swift`, `catalogue.json` |
| SDK views | `playback/SdkViews.kt` | `Components.swift` (mini player, banner); `SDKLiveCardViewRepresentable` in `Screens.swift` |
| Screens | `ui/Screens.kt` | `Screens.swift` |
| Tiles and building blocks | `ui/Tiles.kt`, `ui/Components.kt` | `Tiles.swift`, `Components.swift` |
| Tabs and the mini player | `MainActivity.kt` (`Scaffold` + `NavigationBar`) | `MediaHouseApp.swift` (`TabView`, one `NavigationStack` per tab) |

The screens never call the SDK directly to play something; they call the model
(`playStationInPlace`, `watchStation`, `watchVideo`, `playEpisode`), which picks the right SDK call
and handles the edge cases. The screens read playback state to decide what to highlight.

## Live content from one key

At launch the model calls, in order:

```kotlin
MediaSDK.setup(context)                                         // Android only
MediaSDK.initializePublisher(context, sdkKey, theme)            // stations, branding, news sources
MediaSDK.fetchNews(publisher.publisher.newsSources)             // the publisher's feeds, newest first
```

From the publisher config it derives everything the tabs show:

| Derived | From |
|---|---|
| Radio stations | `players` where `streamChannels.isVideo` isn't `true` |
| TV channels | `players` where `streamChannels.isVideo` is `true` |
| Featured station (Home's live card) | The first player, as ordered on the dashboard |
| News | `fetchNews(publisher.newsSources)` |
| In-feed ad unit | The first station's `gptUrl` (an Ad Manager ad unit path) |

Pull to refresh on Home and News calls `refreshPublisherConfig`, which always goes to the network,
then fetches the news again.

## Four ways to play

Each kind of content uses a different SDK call. This is the core of what the app demonstrates.

| Content | Model method | SDK call | Experience |
|---|---|---|---|
| **Radio** | `playStationInPlace` | `activatePlayer(station)` | Plays without leaving the screen; the mini player is the remote |
| **Live TV** | `watchStation` | `loadPlayer(station)` | The SDK's full-screen player: landscape video, prerolls, PiP, Cast or AirPlay, related channels |
| **Catch-up video** | `watchVideo` | `load(item)`, then `launchPlayer()` | Your own stream in the same full-screen player |
| **Podcast episode** | `playEpisode` | `load(item)` | On-demand audio in place; on Android the mini player adds a progress line |

On-demand items are plain `MediaItem`s the app builds:

```kotlin
fun Video.toMediaItem() = MediaItem(
    id = id,
    streamUrl = url,
    streamChannels = StreamChannels(isAudio = true, isVideo = true),
    streamType = StreamType.VOD,
    metadata = MediaMetadata(title = title, artistOrShow = show),
)
```

```swift
MediaItem(
    id: id,
    streamUrl: URL(string: url)!,
    streamChannels: StreamChannels(isAudio: true, isVideo: true),
    mediaType: .video,
    streamType: .vod,
    metadata: MediaMetadata(title: title, artistOrShow: show)
)
```

`streamChannels.isVideo` decides whether the full-screen player uses its TV layout. The item's `id`
is what the UI compares to highlight the playing video or episode. Both platforms play HLS and plain
files (the demo podcasts are MP3s).

## One mini player for the whole app

Large broadcasters keep "now playing" one tap away wherever you are. The app does it with the SDK's
`SDKMiniPlayerView`:

- **Android:** in the `Scaffold`'s `bottomBar`, above the `NavigationBar`.
- **iOS:** attached to each tab's `NavigationStack` with `safeAreaInset(edge: .bottom)`, so it sits
  above the tab bar and content scrolls clear of it.

It's visible while a stream is loaded and the full-screen player isn't open, and it shows whatever
is playing: a station, a video or an episode, with its own play/pause and close buttons. A tap or an
upward swipe reopens the full-screen player.

## News and in-feed ads

**News** comes from `fetchNews`, which fetches every news source the publisher has on the dashboard
in parallel, merges them and sorts them newest first. Stories open inside the app (a Custom Tab on
Android, `SFSafariViewController` on iOS), so whatever is playing keeps playing.

**The in-feed banner** is the SDK's standalone Ad Manager view, after the third story:

| | Android | iOS |
|---|---|---|
| View | `SDKBannerAdView`, `loadAd(adUnitId)` | `SDKGptBannerViewRepresentable(adUnitId:)` |
| Sizes | 300×250 and 320×50 (the SDK's defaults) | 300×250 frame |
| Until it fills | Collapsed to 1 dp and invisible | Collapsed to 1 pt and invisible |

Both wait for `gptManager.isAdsEnabled` before requesting an ad. Google Mobile Ads takes a moment
to start after launch, and Android's `SDKBannerAdView` drops a request made before then without
retrying.

## Brand colors in the SDK's screens

The model passes the app's brand color to the SDK:

```kotlin
MediaSDK.initializePublisher(context, sdkKey, SDKThemeConfig(primaryColorHex = BRAND_COLOR_HEX))
```

The SDK's full-screen player, live card and mini player then use it, so they look like part of the
app. On Android, the live card still uses each station's own dashboard colors where they're set.

## Edge cases it handles

**Playing after `reset()`.** The mini player's close button calls `reset()`, which clears the
publisher config (and, on Android, the SDK's setup). `load(item)` fails until the SDK is initialized
again. Every play goes through a guard that restores it first, from the cache:

```kotlin
private fun withSdk(action: () -> Unit) = viewModelScope.launch {
    if (!MediaSDK.isInitialized) MediaSDK.setup(context)
    if (MediaSDK.publisherConfig == null) MediaSDK.initializePublisher(context, sdkKey, theme)
    action()
}
```

**Going back to a station after on-demand content.** The last station stays "active" while a video
or episode plays, so `activatePlayer` would only call `play()` and resume the on-demand item. The
model checks what's loaded and calls `load()` to bring the station's stream back:

```kotlin
if (isStationLoaded(station)) MediaSDK.play()
else if (MediaSDK.playerConfig?.assetKey == station.assetKey) MediaSDK.load()
else MediaSDK.activatePlayer(context, station)
```

**Knowing what's playing.** A station counts as playing only when the loaded item is live
(`currentMediaItem.streamType == LIVE`) and it's the active station; on-demand items are matched by
`currentMediaItem.id`. That keeps the station rail from lighting up while an episode plays.

**Stations without a logo.** Tiles fall back to the station's initials ("CT" for Citizen TV).

**The live card on iOS.** Before anything plays, the iOS card for a TV station shows an empty video
area, so Home features the first station as ordered on the dashboard rather than the first TV
channel. In a `LazyVStack` the iOS card also sizes itself wrongly, so Home uses a plain `VStack`.

## Bringing your own content

The demo catalogue (`catalogue.json`, identical on both platforms) has videos (`id`, `title`,
`show`, `summary`, `durationSeconds`, `color`, `url`) and podcast shows with episodes. The streams
are public test content from Mux, Apple and SoundHelix, labelled **Demo content** in the app.

To use your own:

1. Replace `Catalogue.load()` with a call to your CMS or podcast feeds.
2. Keep the `toMediaItem()` / `mediaItem` conversions, adding `artworkUrl` to `MediaMetadata` for
   lock-screen artwork, and `imaUrl` if your on-demand content has prerolls.
3. Replace `GradientArtwork` with your images.

Nothing else changes: the screens and the playback calls already work with any `MediaItem`.

## Possibilities

- **Prerolls on catch-up content.** Give the `MediaItem` an `imaUrl` (a VAST tag) and the SDK plays
  the ad first, with companions, as it does for stations.
- **Artwork on the lock screen.** Set `artworkUrl` in `MediaMetadata` for each video and episode.
- **Resume where you left off.** Store the position of on-demand items (Android:
  `MediaSDK.mediaController?.currentPosition`; iOS: the `AVPlayer` from `avPlayer()`) and seek on the
  next play.
- **A "Live now" schedule.** Pair each station with your programme schedule and show the current
  show on its tile.
- **Personalization.** Order the radio rail by what the listener played last; the SDK's
  `currentMediaItem` tells you what that was.
- **A home-screen banner.** Put an `SDKBannerAdView` / `SDKGptBannerView` between Home's rails with
  its own ad unit.
- **Region editions.** Group stations by the language or region in their names or your own metadata,
  for "Western", "Coast" or "Central" sections.

More in [What else you can build](possibilities.md).
