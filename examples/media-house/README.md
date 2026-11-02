# Media House example

A sample app for **Android** and **iOS** showing how a large publisher (a radio, TV and news
group) can build its whole app around the Atunwa Media SDK with a single **publisher SDK key**.

Where the [starter examples](../README.md) show one screen per integration mode, Media House shows
the SDK spread across a full app, the way leading broadcasters structure theirs:

| Tab | What it shows | SDK calls |
|---|---|---|
| **Home** | A greeting, the SDK's live card, an "On air now" radio rail, live TV tiles, top stories, catch-up videos and podcasts | `SDKLiveCardView`, all of the below |
| **Radio** | Every audio station as a tile, played in place | `activatePlayer`: switches station without leaving the screen |
| **TV** | Live channels, then a "Catch up" list of on-demand videos | `loadPlayer` for live TV; `load(item)` + `launchPlayer` for videos |
| **News** | The publisher's news feeds, with an Ad Manager banner in the feed | `fetchNews(publisher.newsSources)`, `SDKBannerAdView` / `SDKGptBannerView` |
| **Podcasts** | Shows, then episodes, played in place | `load(item)` with an on-demand audio `MediaItem` |

And across the whole app:

- **One mini player above the tab bar**, whatever is playing: a station, a video or an episode.
- **Brand colors in the SDK's own screens**, through `SDKThemeConfig` on `initializePublisher`.
- **Picture-in-Picture** when leaving a playing video, and **lock-screen controls** for audio, both
  handled by the SDK.
- **Pull to refresh** with `refreshPublisherConfig`, and recovery after the mini player's close
  button calls `reset()`.

## Live content and demo content

Stations, their logos, ads and the news feeds come from the **Atunwa dashboard**, through your key.

The SDK doesn't host on-demand videos or podcast episodes; those come from your own CMS. The apps
read a bundled demo catalogue (`catalogue.json`, the same file on both platforms) of public test
streams from Mux, Apple and SoundHelix, labelled **Demo content** in the app. To use your own,
replace the catalogue with calls to your CMS and build a `MediaItem` for each video or episode
(see `Video.toMediaItem()` / `Video.mediaItem`).

## Run it

**Android** (`android/`): copy `local.properties.example` to `local.properties`, set
`ATUNWA_PUBLISHER_SDK_KEY`, then open the folder in Android Studio or run `./gradlew installDebug`.

**iOS** (`ios/`): copy `Config/MediaHouse.local.xcconfig.example` to
`Config/MediaHouse.local.xcconfig`, set `ATUNWA_PUBLISHER_SDK_KEY`, then open
`MediaHouse.xcodeproj` and run. Needs SDK 1.0.6 or later and iOS 16. Set
`GAD_APPLICATION_IDENTIFIER` to your Ad Manager app ID before you ship; without one, the SDK uses
Google's sample app ID.

## Where to look

| | Android | iOS |
|---|---|---|
| Every SDK call | `data/MediaHouseViewModel.kt` | `AppModel.swift` |
| Demo catalogue → `MediaItem` | `data/Catalogue.kt` | `Catalogue.swift` |
| Playback state | `playback/Playback.kt` | `AppModel.swift` (`Playback`) |
| SDK views (live card, mini player, banner) | `playback/SdkViews.kt` | `Components.swift`, `Screens.swift` |
| Screens | `ui/Screens.kt` | `Screens.swift` |

## Notes

- **After `reset()`**, the SDK forgets the publisher (and, on Android, its setup), and `load(item)`
  fails until it's initialized again. The apps check before every play and initialize again from the
  SDK's cache, which is instant.
- **A station that was replaced by on-demand content** still counts as active, so `activatePlayer`
  would just call `play()` and resume the on-demand item. The apps call `load()` to bring the
  station's stream back.
- **In-feed banners wait for Google Mobile Ads to start.** `SDKBannerAdView` (Android) drops a request
  made before then, so the app waits for `gptManager.isAdsEnabled`.

For a walkthrough of how this app is built and what you could add to it, see
[its guide](../docs/media-house.md).
