# What else you can build

The example apps show four patterns. Publishers use the SDK in more ways than that, and most need
only the calls the examples already make. This page collects them: first the overall shapes an
integration can take, then features you can add to any of them. Each one names the SDK feature that
makes it possible and links to its guide.

- [Ways to integrate](#ways-to-integrate)
- [Playback](#playback)
- [Player screens](#player-screens)
- [Advertising](#advertising)
- [Branding](#branding)
- [News](#news)
- [Reliability](#reliability)

---

## Ways to integrate

| Pattern | Who it's for | How | Example |
|---|---|---|---|
| **A station app** | A broadcaster with one radio or TV station | Player API key; `initialize` + `load`, or one call to `launchPlayer(apiKey)` | Starters, `single` mode |
| **A station list** | A publisher with several stations, happy with the SDK's player | Publisher SDK key; `initializePublisher`, then `loadPlayer` or `activatePlayer` per station | Starters, `publisher` mode |
| **A full media app** | A radio, TV and news group with its own content | Publisher key for live and news; `load(item)` for on-demand content from your CMS | [Media House](media-house.md) |
| **"Listen live" inside another app** | A newspaper, a club, a church or a brand that wants its station in an app that's about something else | One button calling `launchPlayer(apiKey)` (iOS: `launchPlayer(apiKey:)`). Nothing to show beforehand, so no `initialize` | One line from the starters |
| **A live card in an existing feed** | An app with a home feed that wants "what's on now" in it | `SDKLiveCardView` with a `previewPlayer`; its play button plays in place and your tap handler opens the player | The live card in every example |
| **An aggregator across publishers** | A directory of many publishers' stations | Build each `PlayerConfig` yourself from your own directory API (its initializer is public) and pass it to `loadPlayer` / `activatePlayer`, with no per-publisher key | [API reference](../../ios/docs/api-reference.md) |
| **Your own player screen** | An app whose design needs a different full-screen player | `SDKVideoView` (Android: `attachVideoView`; iOS: `createVideoView()`), your controls driven by the SDK's state, the ad surface attached | [Android](../../android/docs/media-playback.md#building-a-custom-player-screen), [iOS](../../ios/docs/media-playback.md#building-a-custom-player-screen) |
| **An embedded audio player** | A screen with "now playing" built in, not presented over it | `SDKAudioPlayerView`: title, progress, play/pause (and on iOS artwork, waveform, volume and a companion slot), fully reactive | [Android](../../android/docs/ui-components.md#sdkaudioplayerview), [iOS](../../ios/docs/ui-components.md#sdkaudioplayerview) |
| **Cross-platform** | Flutter and React Native apps | The native SDKs behind a bridge or module you own | [Flutter](flutter.md), [React Native](react-native.md) |

## Playback

| Idea | How |
|---|---|
| **Podcasts and catch-up from your CMS** | Build a `MediaItem` per episode or video (`streamType` VOD, `streamChannels.isVideo` for video) and call `load(item)`. HLS and plain files both play |
| **Lock-screen artwork** | Set `artworkUrl` in each item's `MediaMetadata` |
| **Rewind live radio** (iOS) | `canSeek()` and `seek(by:)` for streams with a DVR window. On Android, seek through `MediaSDK.mediaController` |
| **Your own quality menu** (iOS) | `availableQualities`, `selectQuality(_:)`, `selectedQualityPublisher`: the stream's own variants. Android's player has its own picker |
| **Casting** | Built in: Google Cast on Android and AirPlay on iOS, from the full-screen player |
| **Resume on-demand content** | Save the position (Android: `mediaController.currentPosition`; iOS: `avPlayer()`), then seek after the next `load(item)` |
| **"Now playing" everywhere** | Observe `currentMediaItem` and show its metadata in your own widgets, as the examples do to highlight rows |

Details: Media playback ([Android](../../android/docs/media-playback.md), [iOS](../../ios/docs/media-playback.md)).

## Player screens

| Idea | How |
|---|---|
| **Video in portrait** | `videoFullscreenByDefault = false`; the player opens video in its portrait layout, with stream info and related channels below |
| **Back returns to your app** | `videoPipOnBack = false` closes the player instead of minimizing a playing video into Picture-in-Picture |
| **A TV entry point** | Filter the publisher's players for `streamChannels.isVideo` and open one with `loadPlayer` |
| **Related channels** | In publisher mode, the player shows a rail of the publisher's other TV stations. Keep `publisherConfig` loaded (call `initializePublisher` again after `reset()`) for it to show |
| **A mini player that fits your app** | `SDKMiniPlayerView` with your own `onExpandListener` and `onCloseClickListener`; hide it while `isDedicatedPlayerActive` |

Details: UI components ([Android](../../android/docs/ui-components.md), [iOS](../../ios/docs/ui-components.md)).

## Advertising

Stations' ads are configured on the dashboard, so most of this needs no code.

| Idea | How |
|---|---|
| **Prerolls on on-demand content** | Give the `MediaItem` an `imaUrl` (a VAST tag). The SDK plays it, with companions, before the content |
| **Banners anywhere** | `SDKBannerAdView` (Android) or `SDKGptBannerView` (iOS) with an Ad Manager ad unit, in a feed, between home rails or under an article. Wait for `gptManager.isAdsEnabled` before loading |
| **Ad-aware controls** | Observe `adPlaybackState` and `adCountdown` to show "Ad · 0:12" or disable your own controls during an ad |
| **Tuned prerolls** | `imaConfig` (language, timeouts, debug mode) before the first ad-enabled load |
| **Header bidding** | Amazon APS bids are attached to banner requests on Android when configured; on iOS, supply bids through `apsBidProvider` |
| **Consent** | Collect consent with a CMP such as Google's User Messaging Platform before ads load; Google's ad SDKs read the IAB TCF values it stores |
| **Your own Ad Manager app ID** (iOS) | Set `GADApplicationIdentifier`. Without it, SDK 1.0.6+ uses Google's sample app ID and reports `gptManager.isUsingTestAppId` |

Details: Advertising ([Android](../../android/docs/ad-integration.md), [iOS](../../ios/docs/ad-integration.md)).

## Branding

| Idea | How |
|---|---|
| **The SDK's screens in your colors** | `SDKThemeConfig` (primary, secondary, live badge, loading colors) on `initialize` or `initializePublisher`, as Media House does |
| **Each station in its own colors** (Android) | Set colors per station on the dashboard; `SDKLiveCardView` prefers them over the app theme |
| **Your UI in the station's colors** | Read `SDKThemeManager`'s current theme in your own views |
| **Station logos** | `PlayerConfig.logo`, and the publisher's in `PublisherInfo.logo`. Fall back to initials when a station has none, as Media House does |

Details: Theming ([Android](../../android/docs/theming.md), [iOS](../../ios/docs/theming.md)).

## News

| Idea | How |
|---|---|
| **A news tab** | `fetchNews(publisher.newsSources)`: every feed on the dashboard, in parallel, merged and newest first |
| **"Listen while you read"** | Open articles inside the app (Custom Tabs, `SFSafariViewController`) so playback carries on |
| **Endless scrolling** | Feeds have no cursor; reveal more of the fetched list as the user scrolls |
| **Per-station news** | Each `PlayerConfig.publisher.newsSources` holds a station's own feeds |

Details: News ([Android](../../android/docs/news.md), [iOS](../../ios/docs/news.md)).

## Reliability

These are built in; an app only needs to not work against them.

| Behavior | What it means for your app |
|---|---|
| **Offline start** | `initialize` and `initializePublisher` fall back to the last config saved on disk, so the app opens with its stations without a network |
| **Retries and stall recovery** | Failed or stalled streams are retried with backoff before the state goes to `error` |
| **Network changes** | Playback waits for the network to return before retrying |
| **Fresh data on demand** | `refreshPlayerConfig` / `refreshPublisherConfig` skip the cache, so pull-to-refresh reports a real failure |
