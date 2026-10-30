# UI components

Every SDK view is a `@MainActor` UIKit view that subscribes to `MediaSDK.shared` and
`SDKThemeManager.shared` on its own. You add it to your layout and wire up its callbacks; you never
push state into it.

| Kind | Views | Notes |
|---|---|---|
| **Drop-in screens and widgets** | `SDKLiveCardView`, `SDKMiniPlayerView`, `SDKAudioPlayerView`, `SDKPlayerViewController` | Fully reactive |
| **Video** | `SDKVideoView` | An `AVPlayerLayer` host for your own player screens |
| **Ad views** | `SDKBannerAdView`, `SDKGptBannerView`, `SDKCompanionBannerView` | See [Advertising](ad-integration.md) |
| **Building blocks** | `SDKWaveformView`, `SDKLiveBadgeView`, `SDKShakeMeView` | |

SwiftUI wrappers: `SDKLiveCardViewRepresentable`, `SDKVideoViewRepresentable`,
`SDKWaveformRepresentable`, `SDKLiveBadgeRepresentable`, `SDKGptBannerViewRepresentable`.

- [SDKLiveCardView](#sdklivecardview)
- [SDKMiniPlayerView](#sdkminiplayerview)
- [SDKPlayerViewController (built-in full-screen player)](#sdkplayerviewcontroller-built-in-full-screen-player)
- [SDKAudioPlayerView](#sdkaudioplayerview)
- [SDKVideoView](#sdkvideoview)
- [Ad views](#ad-views)
- [Building blocks](#building-blocks)
- [A typical home screen](#a-typical-home-screen)

---

## SDKLiveCardView

A "what's live right now" card for your home screen: station logo, a LIVE/AD badge, a stream-type
pill, title and publisher, a play/pause button with a status label, a waveform (audio stations) or an
embedded video surface (video stations), a companion-ad slot and, for radio stations, its own GPT
banner.

```swift
let card = SDKLiveCardView()
card.previewPlayer = defaultStation          // shown until something is active
card.onCardClickListener = { [weak self] in  // the card itself, not its play button
    MediaSDK.shared.launchPlayer(from: self)
}
```

```swift
// SwiftUI
SDKLiveCardViewRepresentable(previewPlayer: defaultStation) {
    MediaSDK.shared.launchPlayer()
}
```

| Property | Type | Effect |
|---|---|---|
| `previewPlayer` | `PlayerConfig?` | A station shown statically before anything is loaded. Its play button activates it in place with `activatePlayer(_:)`. Once any station is active, the card shows that station instead |
| `onCardClickListener` | `(() -> Void)?` | Tap on the card (not the play button). The card never opens a player itself |

Ads on the card: video stations get the IMA preroll in the embedded surface plus a VAST companion.
Audio stations get the GPT banner during content, and the media area expands for the length of an
IMA ad. While the full-screen player exists (`isDedicatedPlayerActive`), the card leaves the shared
ad surface to it.

## SDKMiniPlayerView

A 62 pt persistent bar for the bottom of your screens: logo, station name with a LIVE badge, a status
area (waveform while playing, spinner while loading or buffering, an "AD" pill during an ad),
play/pause and close.

```swift
let mini = SDKMiniPlayerView()
mini.onExpandListener = { [weak self] in MediaSDK.shared.launchPlayer(from: self) }  // tap or swipe up
mini.onCloseClickListener = { MediaSDK.shared.reset() }
```

| Property | Type | Effect |
|---|---|---|
| `onExpandListener` | `(() -> Void)?` | Tap anywhere or swipe up |
| `onCloseClickListener` | `(() -> Void)?` | The close button. Usually `reset()` |

Show it while something is active and the full-screen player isn't:
`state != .idle && !MediaSDK.shared.isDedicatedPlayerActive`. Because the player counts as active
while it's in Picture-in-Picture, the bar stays hidden under a PiP window and appears once the
window is closed.

## SDKPlayerViewController (built-in full-screen player)

A complete player screen. You don't create it yourself; you present it:

```swift
MediaSDK.shared.launchPlayer(from: self)                        // show whatever is active
MediaSDK.shared.launchPlayer(apiKey: PLAYER_API_KEY, from: self) // single-player: activate + show
MediaSDK.shared.loadPlayer(station, from: self)                 // publisher: activate + show
```

It's presented full screen inside the SDK's own navigation controller, which hands status bar, home
indicator and orientation decisions to the player.

| Feature | Detail |
|---|---|
| **Adaptive layout** | Picked from `streamChannels.isVideo`. **Radio**: a 200 pt circle with the station logo (swapped for a companion ad or GPT banner while one is showing), live badge, title, waveform, play/pause, status. **Video**: a 16:9 video surface with on-video controls |
| **Full-screen video** | Video opens full screen in landscape (`videoFullscreenByDefault`). Tap the video for play/pause, a full-screen toggle and (in full screen) a minimize button. Turning the device also switches modes. See [Full-screen video](media-playback.md#full-screen-video) |
| **Picture-in-Picture** | Closing a playing video minimizes it into PiP (`videoPipOnBack`); so does leaving the app. Restoring brings this same screen back; closing the window pauses. See [Picture-in-Picture](media-playback.md#picture-in-picture) |
| **AirPlay** | An AirPlay route button in the navigation bar (Android's Cast button) |
| **Quality** | A picker over the stream's own variants (`availableQualities`), shown only when the stream offers more than one |
| **Share** | Shares the station title, show name and stream URL through the system share sheet |
| **Related channels** | In publisher mode, a video station's portrait layout shows a rail of the publisher's other video stations. Tapping one switches station in place |
| **Ads** | IMA prerolls render over the video (or the artwork for radio). For radio, IMA VAST and AdsWizz SSI companions (300×250) take the station logo's place while they show, ahead of the GPT banner, which takes it otherwise and pauses during a companion. For TV, the GPT banner and IMA companions render below the video. Nothing is requested into a spot that's hidden by full screen. See [Advertising](ad-integration.md) |
| **Station switches** | A station switch from anywhere (related channels, your own list) keeps the screen open and re-renders it. `reset()` closes it |

There's only ever one instance. `launchPlayer` while it's on screen does nothing; while it's in PiP,
it brings that instance back.

## SDKAudioPlayerView

A UIKit audio player view you can embed in your own screen instead of presenting
`SDKPlayerViewController`: a 160 pt artwork circle, station and show labels, a live badge, a
waveform, play/pause, a volume slider and a 300×250 companion slot. No properties to set; it follows
`MediaSDK.shared`.

```swift
let audioPlayer = SDKAudioPlayerView()
container.addSubview(audioPlayer)
```

## SDKVideoView

A plain `AVPlayerLayer` host for video in your own layouts.

```swift
let videoView = MediaSDK.shared.createVideoView()   // bound to the shared player, PiP set up
// or, for a view you created yourself:
MediaSDK.shared.attachVideoView(myVideoView)
```

```swift
// SwiftUI
MediaSDK.shared.createVideoViewRepresentable()
    .aspectRatio(16 / 9, contentMode: .fit)
```

| API | Notes |
|---|---|
| `init(player: AVPlayer? = nil)` | |
| `playerLayer` | The `AVPlayerLayer`. Resized with the view |
| `attach(player:)` | Points the layer at a player |

`createVideoView()`, `attachVideoView(_:)` and the representable set up Picture-in-Picture on the
view's layer, so `MediaSDK.shared.togglePiP()` works with it.

## Ad views

| View | Use |
|---|---|
| `SDKBannerAdView` | Player-tied GPT banner: loads the current item's `gptUrl`, waits for the IMA preroll, refreshes every 30–60 s. `isSuppressed` stops it while its spot is hidden |
| `SDKGptBannerView` | Standalone GPT banner for any screen, with an explicit `adUnitId` |
| `SDKCompanionBannerView` | Hosts AdsWizz companion creatives. Toggle its visibility from `hasCompanionAdPublisher` |

Details in [Advertising → Ad views reference](ad-integration.md#ad-views-reference).

## Building blocks

### SDKWaveformView

A five-bar animated waveform matching Android's. Feed it `MediaSDK.shared.amplitude`:

```swift
let waveform = SDKWaveformView()
waveform.barColor = .systemBlue
MediaSDK.shared.amplitude
    .sink { [weak waveform] value in waveform?.updateAmplitude(value) }
    .store(in: &cancellables)
```

SwiftUI: `SDKWaveformRepresentable(amplitude:color:)`. Call `updateAmplitude(_:)` on the main thread.

### SDKLiveBadgeView

A "● LIVE" pill with a pulsing dot.

```swift
let badge = SDKLiveBadgeView()
badge.color = UIColor(hex: theme.liveBadgeColorHex)
badge.isAnimating = isPlaying
```

SwiftUI: `SDKLiveBadgeRepresentable(color:isAnimating:)`.

### SDKShakeMeView

A promotional widget that fires a callback when the user shakes the device.

```swift
let shake = SDKShakeMeView()
shake.promptText = "Shake for a prize!"
shake.onShakeDetected = { openPromo() }
shake.startMonitoring()      // when it appears
// shake.stopMonitoring()    // when it disappears
```

Tunable: `shakeThreshold` (default 2.5 g), `minimumShakeInterval` (1 s), `samplingInterval` (0.1 s).

## A typical home screen

```swift
final class HomeViewController: UIViewController {
    private let liveCard = SDKLiveCardView()
    private let miniPlayer = SDKMiniPlayerView()
    private var cancellables = Set<AnyCancellable>()

    override func viewDidLoad() {
        super.viewDidLoad()
        liveCard.previewPlayer = MediaSDK.shared.publisherConfig?.players.first
        liveCard.onCardClickListener = { [weak self] in MediaSDK.shared.launchPlayer(from: self) }

        miniPlayer.onExpandListener = { [weak self] in MediaSDK.shared.launchPlayer(from: self) }
        miniPlayer.onCloseClickListener = { MediaSDK.shared.reset() }

        MediaSDK.shared.statePublisher
            .combineLatest(MediaSDK.shared.isDedicatedPlayerActivePublisher)
            .receive(on: DispatchQueue.main)
            .sink { [weak self] state, playerOpen in
                self?.miniPlayer.isHidden = state == .idle || playerOpen
            }
            .store(in: &cancellables)

        // …add liveCard (top), your station list, and miniPlayer (bottom) with constraints…
    }
}
```
