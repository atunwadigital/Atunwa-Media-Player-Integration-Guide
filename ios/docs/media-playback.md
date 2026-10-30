# Media playback & state

- [Loading media](#loading-media)
- [Playback controls](#playback-controls)
- [The state machine](#the-state-machine)
- [Publishers](#publishers)
- [Full-screen video](#full-screen-video)
- [Picture-in-Picture](#picture-in-picture)
- [Background audio, lock screen and AirPlay](#background-audio-lock-screen-and-airplay)
- [Quality and seeking](#quality-and-seeking)
- [Offline start & config caching](#offline-start--config-caching)
- [Engine behavior](#engine-behavior)
- [Building a custom player screen](#building-a-custom-player-screen)

---

## Loading media

### The active station's stream

```swift
try MediaSDK.shared.load()
```

`load()` builds a live `MediaItem` from `playerConfig`: its stream URL, stream channels, IMA ad tag
(`adUrl`), GPT ad unit (`gptUrl`), and metadata (title = station name, artist = publisher name or
website, artwork = logo). It throws `SDKError.notInitialized` if there's no active station.

### A custom `MediaItem`

```swift
let item = MediaItem(
    id: "episode-42",
    streamUrl: URL(string: "https://cdn.example.com/episode-42.m3u8")!,
    streamChannels: StreamChannels(isAudio: true, isVideo: false),
    streamType: .vod,
    metadata: MediaMetadata(title: "Episode 42", artistOrShow: "Morning Show", artworkUrl: coverURL),
    imaUrl: nil,
    gptUrl: nil
)
try MediaSDK.shared.load(item: item)
```

`load(item:)` requires the SDK to be initialized (by `initialize`, `initializePublisher` or a station
switch); otherwise it throws `SDKError.notInitialized`. It publishes the item on
`currentMediaItemPublisher`, moves the state to `.loading`, updates the lock screen, requests the
IMA preroll if the item has an `imaUrl`, and starts playing if autoplay is on (the default).

`hls://` URLs are normalized to `https://`.

## Playback controls

| Call | Effect |
|---|---|
| `play()` | Starts or resumes playback. If called while a live AdsWizz load is still decorating its URL, it's applied as soon as the player exists |
| `pause()` | Pauses playback |
| `reset()` | Stops playback, clears the item and both configs, returns to `.idle`, closes the player and PiP |
| `launchPlayer(from:)` | Presents `SDKPlayerViewController` for whatever is active (or brings back the one in PiP) |

`play()` and `pause()` do nothing until the SDK is initialized.

## The state machine

```
idle ──► initializing ──► idle
  │                          
  └──► loading ──► ready / buffering / playing / adPlaying
                     │
       playing ◄──► paused        playing ◄──► buffering
       playing ──► adPlaying ──► playing
       any ──► error ──► idle / initializing / loading
```

| State | Meaning |
|---|---|
| `.idle` | Nothing loaded (initial, and after `reset()`) |
| `.initializing` | Fetching player config |
| `.loading` | The item is loading |
| `.ready` | Loaded but not playing |
| `.playing` | Playing |
| `.paused` | Paused by the user, the app, or an interruption |
| `.buffering` | Waiting for data mid-stream |
| `.adPlaying` | An IMA ad is playing; content is suspended |
| `.error` | Unrecoverable failure (after retries) |

Invalid transitions are rejected and logged (`MediaSDK: Invalid state transition: …`). `reset()`
always forces `.idle`.

### Observing state

```swift
MediaSDK.shared.statePublisher
    .receive(on: DispatchQueue.main)
    .sink { [weak self] state in self?.render(state) }
    .store(in: &cancellables)
```

In SwiftUI, wrap the publishers in an `ObservableObject` (the sample app's `SDKStateObserver` in
`MainView.swift` does this), or use `.onReceive(MediaSDK.shared.statePublisher)`.

## Publishers

| Publisher | Type | Snapshot |
|---|---|---|
| `statePublisher` | `SDKState` | `currentState` |
| `playWhenReadyPublisher` | `Bool`: play/pause *intent* — stays `true` while buffering | `playWhenReady` |
| `currentMediaItemPublisher` | `MediaItem?` | `currentMediaItem` |
| `adPlaybackStatePublisher` | `AdPlaybackState` | `currentAdPlaybackState` |
| `adCountdownPublisher` | `Int`, seconds left in the ad | |
| `hasCompanionAdPublisher` | `Bool` | |
| `isDedicatedPlayerActivePublisher` | `Bool`: the full-screen player exists (on screen or in PiP) | `isDedicatedPlayerActive` |
| `amplitude` | `PassthroughSubject<Float, Never>`, 0–1 | |
| `dismissPlayerSignal` | `PassthroughSubject<Void, Never>` | |

All of them emit on the main actor.

`playWhenReady` is what a play/pause button should reflect: it says whether the user asked to play,
even while the stream is still buffering. It goes `false` during an IMA ad break, because the content
is paused for the ad.

## Full-screen video

The built-in player opens video stations **full screen in landscape**: the video fills the screen
and the status bar, navigation bar and home indicator hide. Tapping the video shows its controls:
play/pause, a button to switch to the portrait layout (video above stream info, related channels and
banners) and back, and in full screen a minimize button.

- **Rotate to full screen.** With the device's rotation lock off, turning the device does the same
  as the button: landscape enters full screen, portrait exits it. Right after the player opens, or
  after the button is used, rotation is ignored until the device is held the way the current mode
  expects, so it never undoes that choice straight away.
- **Turning it off.** Set `MediaSDK.shared.videoFullscreenByDefault = false` to open video in the
  portrait layout. The viewer can still go full screen.
- **Host requirement.** The app must support the landscape orientations. The player only asks for
  landscape while it's on screen, and puts the app back in the orientation it was in when it closes.
- **Ads.** While the video is full screen, the below-video Ad Manager banner isn't requested and the
  IMA companion slot isn't offered to IMA, so no impressions are counted for ads nobody can see. Both
  come back as soon as the viewer leaves full screen.

Radio stations always use the normal player layout.

## Picture-in-Picture

- **Minimize.** Closing the built-in player while a video is playing (or showing its preroll)
  shrinks it into a PiP window instead (`MediaSDK.shared.videoPipOnBack`, default `true`). Radio,
  paused video and devices without PiP close as before.
- **Leaving the app.** A video playing in the built-in player also floats into PiP when the user
  goes home.
- **Restore.** PiP's own "back to app" button, or calling `launchPlayer(from:)` / `loadPlayer` for
  the same station, brings the same player screen back and closes the window. There's never a second
  player.
- **Close.** Closing the PiP window pauses playback, so the video's audio doesn't keep playing with
  nothing on screen.
- **Station change.** Activating a different station, or `reset()`, closes the PiP window without
  pausing the new stream.
- **State.** `isDedicatedPlayerActive` stays `true` while the player is in PiP, so a mini player that
  hides on it doesn't appear underneath the PiP window. `isPictureInPictureActive` tells you whether a
  PiP window is up.

For video you show yourself in an `SDKVideoView`, `createVideoView()`/`attachVideoView(_:)` set PiP
up on that view's layer and `togglePiP()` starts or stops it.

PiP needs the `audio` background mode, and doesn't work in the iOS Simulator.

## Background audio, lock screen and AirPlay

- The SDK sets the audio session to `.playback` (mode `.moviePlayback` for video, `.default` for
  audio). With the `audio` background mode, playback continues with the screen locked and the app in
  the background.
- The lock screen and Control Center show the station name, the publisher or website, and play/pause
  controls.
- Audio-session interruptions (calls, Siri, other apps) pause playback and resume it afterwards if it
  was playing.
- The built-in player has an AirPlay button. In your own UI, add an `AVRoutePickerView`; the shared
  `AVPlayer` follows the route the user picks.

## Quality and seeking

The quality options come from the stream itself: the SDK reads the loaded HLS stream's variants
(`#EXT-X-STREAM-INF`) and offers one option per distinct quality, labelled by resolution for video
("1080p", "720p60") or by bitrate for audio ("128 kbps"):

```swift
MediaSDK.shared.availableQualities           // [.auto, 1080p, 720p, ...] for the current stream
MediaSDK.shared.availableQualitiesPublisher  // emits when a new stream's variants are read
MediaSDK.shared.selectQuality(option)        // one of availableQualities
MediaSDK.shared.selectedQuality              // resets to .auto for each station
```

A stream with only one variant offers just `.auto`, and the built-in player hides its quality
button. AVFoundation can't pin a variant the way Android's track selector does, so a choice caps
the peak bitrate and resolution at that variant's values: the player plays it, or a lower one if the
network can't keep up.

Live streams that expose a DVR window can be scrubbed:

```swift
if MediaSDK.shared.canSeek() {
    MediaSDK.shared.seek(by: -30)   // rewind 30 s, clamped to the seekable range
}
```

## Offline start & config caching

- Every successful `initialize`/`initializePublisher` fetch is cached in memory and persisted to
  `UserDefaults` (keys prefixed `atunwa_mediasdk_`).
- If the network fetch fails after its retries, `initialize`/`initializePublisher` fall back to the
  persisted copy, so the app can start offline with the last-known stations.
- `preload` only fills the in-memory cache.
- `refreshPlayerConfig`/`refreshPublisherConfig` never fall back to a cache.

## Engine behavior

- **Retries.** A failed item is retried up to 3 times with backoff before the state goes to `.error`.
- **Stall recovery.** Buffering that lasts more than 15 seconds is treated as a failure and recovered
  the same way (the "buffers for a while, then stops" symptom on flaky networks).
- **Network changes.** The SDK watches connectivity and waits for the network to come back before a
  retry.
- **Live edge.** Live streams stay at the live edge.
- **Amplitude.** A real RMS level is read from the decoded audio (`MTAudioProcessingTap`) and
  published on `amplitude`, which drives `SDKWaveformView`.
- **AdsWizz.** For live streams, the stream URL is decorated by the AdsWizz SDK before playback so
  server-side ad breaks can carry companion banners.

## Building a custom player screen

If you build your own full-screen player instead of using `SDKPlayerViewController`:

1. Bind your UI to `statePublisher`, `playWhenReadyPublisher` and `currentMediaItemPublisher`, never
   to the `AVPlayer` directly. During an IMA ad the content is paused and the state is `.adPlaying`.
2. Show video in an `SDKVideoView` from `createVideoView()` (UIKit) or `createVideoViewRepresentable()`
   (SwiftUI), which also sets up PiP.
3. Attach the IMA ad surface so prerolls render on your screen:
   `SDKAdDisplayContainer.shared.attachAdSurface(to: yourVideoContainer, viewController: self)`.
4. Call `incrementDedicatedPlayerActive()` when your screen appears and
   `decrementDedicatedPlayerActive()` when it disappears, so `SDKLiveCardView` doesn't take the ad
   surface away from it.
5. Add an `SDKBannerAdView` and an `SDKCompanionBannerView` where you want banners and companions.

The sample app's SwiftUI `PlayerView` is a complete example of a host-built player.
