# Example apps: how they're built

These guides walk through each example app: how it's set up, how each screen calls the SDK, the
edge cases it handles, and what else you could build on the same pattern. Read them next to the
code; every section names the file it describes.

| App | Platforms | Key | Pattern | Guide |
|---|---|---|---|---|
| **Starter** | Android (Compose) | Player *or* publisher | One screen per integration mode | [Android starter](android.md) |
| **Starter** | iOS (SwiftUI) | Player *or* publisher | One screen per integration mode | [iOS starter](ios.md) |
| **Starter** | Flutter (Android + iOS) | Player *or* publisher | Dart UI over a platform-channel bridge | [Flutter](flutter.md) |
| **Starter** | React Native (Android + iOS) | Player *or* publisher | JS UI over a native module | [React Native](react-native.md) |
| **Media House** | Android (Compose) + iOS (SwiftUI) | Publisher | A full broadcaster app: home, radio, TV, news, podcasts | [Media House](media-house.md) |

Then [What else you can build](possibilities.md) collects ideas that go beyond the examples, each
pointing at the SDK feature that makes it possible.

## Which one to start from

| You're building | Start from |
|---|---|
| An app for **one station** (a single radio or TV channel) | A starter in `single` mode |
| A **station list** for a publisher, with the SDK's player doing the rest | A starter in `publisher` mode |
| A **full media app**: home feed, sections, news, on-demand content, one player across it all | Media House |
| A **Flutter** or **React Native** app | The Flutter or React Native starter. The bridge in each is the one to copy |

## What every app has in common

Whatever the platform or pattern, the apps follow the same shape.

**1. Get a key from Atunwa, keep it out of source control.** Each app reads its key from a
git-ignored file at build time, so nothing secret is committed. Player API keys and publisher SDK
keys are both safe to ship inside an app; they identify content, they aren't server secrets.

| App | Key file (git-ignored) | Template |
|---|---|---|
| Android starter | `local.properties` | `local.properties.example` |
| iOS starter | `Config/Atunwa.local.xcconfig` | `Config/Atunwa.local.xcconfig.example` |
| Flutter | `atunwa.json` (`--dart-define-from-file`) | `atunwa.example.json` |
| React Native | `atunwa.config.json` | `atunwa.config.example.json` (copied on `npm install`) |
| Media House Android | `local.properties` | `local.properties.example` |
| Media House iOS | `Config/MediaHouse.local.xcconfig` | `Config/MediaHouse.local.xcconfig.example` |

**2. Start the SDK once, early.** Android calls `MediaSDK.setup(context)` before any SDK view is
shown. iOS touches `MediaSDK.shared` at launch so the ad SDKs have started before the first stream.
The Flutter and React Native bridges expose this as `AtunwaMedia.setup()`.

**3. Fetch content with the key.** `initialize(apiKey)` for one station, or
`initializePublisher(sdkKey)` for a publisher. Both cache to memory and disk, so the apps also start
offline with the last-known stations.

**4. Let the SDK play.** The apps never create a player of their own. They hand the SDK a station
(`loadPlayer`, `activatePlayer`, `load()`) or a `MediaItem` (`load(item)`), and use the SDK's
full-screen player, live card and mini player. Ads, background audio, lock-screen controls,
Picture-in-Picture, AirPlay and Cast all come with it.

**5. Observe state, don't drive it.** The UI listens to the SDK's state (`state`,
`playWhenReady`, `currentMediaItem`, `isDedicatedPlayerActive`) to decide what to show: which row
is "Now playing", whether to show the mini player, play or pause icons.

**6. Recover after `reset()`.** The mini player's close button calls `reset()`, which stops playback
and clears the active station *and* the publisher config. Every app initializes again (from the
cache, so it's instant) before the next play.

## Two SDK behaviors every integration should know

These shaped the examples' code, and they're easy to trip over.

- **`initialize()` makes the station active without loading its stream.** After it, the SDK
  treats the station as already playing: `launchPlayer(apiKey)`, `loadPlayer(station)` and the live
  card's play button won't start the stream. Call `load()` to start it. The single-player screens
  do exactly this.
- **A station replaced by on-demand content still counts as active.** If you `load(item)` a video or
  episode, then ask for the station again with `activatePlayer`, the SDK only calls `play()`, which
  resumes the on-demand item. Call `load()` to bring the station's stream back. Media House does
  this in `playStationInPlace`.
