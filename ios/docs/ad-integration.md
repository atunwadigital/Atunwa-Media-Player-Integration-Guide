# Advertising

The SDK runs three ad integrations, all configured per station in the Atunwa dashboard. You don't
write ad code to get them; you only provide the app-level setup below. Every integration is
best-effort: if an ad fails or times out, the stream still plays.

| Integration | Configured by | Renders in |
|---|---|---|
| IMA prerolls + VAST companions | the station's `imaUrl` (decoded as `PlayerConfig.adUrl`) | Over the video (or the radio artwork); companions in the companion slot |
| Google Ad Manager banners | the station's `gptUrl` | `SDKBannerAdView` (player screen, live card) |
| AdsWizz SSI companions | live audio streams (compiled-in zone 2459) | `SDKCompanionBannerView` |

## Setup checklist

- [ ] `GADApplicationIdentifier` in Info.plist, for Ad Manager banners (optional).
- [ ] Optionally tune IMA (`MediaSDK.shared.imaConfig`) before the first ad-enabled load.
- [ ] Optionally plug in header bidding (`MediaSDK.shared.apsBidProvider`).
- [ ] If you build your own player screen, attach the IMA ad surface (see below).

## 1. IMA prerolls & VAST companions

When an item with an `imaUrl` loads, the ad state goes to `.requesting` immediately and the SDK
requests the ad from IMA once the player exists and a real screen is showing (the full-screen player,
a live card, or your own screen). While the ad plays, content is paused and the state is
`.adPlaying`; when the ad ends or errors, content resumes.

IMA renders its UI (skip button, countdown, "learn more") into one persistent ad surface owned by
`SDKAdDisplayContainer.shared`. Screens borrow it:

```swift
// In your own player screen (the SDK's own views already do this):
SDKAdDisplayContainer.shared.attachAdSurface(to: videoContainerView, viewController: self)
```

Once an ad session starts, it stays on the screen it started on until it ends; IMA crashes if its
view moves to another view controller mid-ad. A dedicated player that calls
`incrementDedicatedPlayerActive()` always wins the surface over `SDKLiveCardView`.

### VAST companions

The SDK registers a 320×50 companion slot backed by `SDKAdDisplayContainer.shared.companionContainerView`.
The built-in player and the live card put that view in their companion area; in your own screen, add
it to your layout and size it yourself. `hasCompanionAdPublisher` emits `true` when an ad with a
companion starts; if nothing has rendered into the slot 3 s later (IMA doesn't report rendering), it
goes back to `false`. Show the area only while it's `true`.

While the built-in player's video is full screen, the companion area is hidden, so the SDK doesn't
offer the slot to IMA. The change applies from the next ad request.

### Tuning IMA (`IMAConfig`)

```swift
var ima = MediaSDK.shared.imaConfig
ima.vastLoadTimeoutMs = 6_000
ima.language = "fr"
ima.addFriendlyObstruction(myLogoOverlay, purpose: .notVisible, detailedReason: "Watermark")
MediaSDK.shared.imaConfig = ima
```

| Field | Default | |
|---|---|---|
| `language` | `"en"` | IMA UI language |
| `vastLoadTimeoutMs` | `4000` | VAST response timeout |
| `mediaLoadTimeoutMs` | `5000` | Ad media load timeout |
| `enableDebugMode` | `false` | IMA debug logging |
| `maxRedirects` | `4` | VAST wrapper redirects |
| `sessionId` | `nil` | Sent to IMA as the PPID |
| `playerType` / `playerVersion` | `"atunwa-media-sdk"` / `"1.0.0"` | Reported to IMA |
| `enableFocusSkipButton`, `adAttribution`, `adCountDown` | `true` | IMA UI elements |
| `friendlyObstructions` | `[]` | Views that overlay the ad without counting as obstructions (Open Measurement) |

Set it before the first ad-enabled load.

## 2. Display banners (Ad Manager + Amazon APS)

`SDKBannerAdView` loads the current item's `gptUrl` as an Ad Manager ad unit.

### Request flow

1. The view decides with `AdSequencer` whether it may load: never while an IMA ad is playing, and,
   for a new item with a preroll, not until that preroll has resolved (or a 12 s wait has passed).
2. It picks the sizes that fit its own bounds from `[300×250, 320×50]` (falling back to all sizes if
   none fit).
3. It asks `MediaSDK.shared.apsBidProvider` for header-bidding key/values and adds them, plus
   `lrp_domain`, `lrp_publisher_name` and `lrp_play_name`, as custom targeting.
4. It loads the request.

**Amazon APS.** Amazon's iOS SDK isn't distributed through Swift Package Manager, so the SDK ships a
seam rather than the integration. Supply your own `APSBidProvider` to add bids:

```swift
struct AmazonBids: APSBidProvider {
    func fetchBids(adUnitId: String, sizes: [CGSize]) async -> [String: String] {
        // Call Amazon's SDK here; return its custom params, or [:] on failure.
    }
}
MediaSDK.shared.apsBidProvider = AmazonBids()
```

The default `NoOpAPSBidProvider` returns no bids, so requests go out GPT-only.

### Lifecycle

- Refreshes every 30–60 s (random), with a 10 s "no ad" gap before each refresh. During the gap the
  view shows a small waveform if content is playing, or hides.
- `onContentStateChanged(Bool)` tells you whether the banner is showing something. Use it, not
  "a `gptUrl` is configured", to decide what to show in its place (a load can fail or not fill).
- `isSuppressed = true` stops all requests and hides the banner, for when its spot is hidden.
  Setting it back to `false` loads straight away if an ad is due. The built-in player suppresses its
  below-video banner while the video is full screen.
- Without `GADApplicationIdentifier`, banners stay off and the view stays hidden.

### Using it in your own layouts

```swift
let banner = SDKBannerAdView()          // follows the current station's gptUrl
banner.onContentStateChanged = { [weak self] showing in self?.discView.isHidden = showing }
```

For a banner on any screen, unrelated to the player (a station list, a settings page), use
`SDKGptBannerView` with an explicit ad unit:

```swift
let banner = SDKGptBannerView(adUnitId: "/1234/home-banner")
banner.customTargeting = ["section": "home"]
```

```swift
// SwiftUI
SDKGptBannerViewRepresentable(adUnitId: "/1234/home-banner")
    .frame(height: 50)
```

## 3. AdsWizz server-side insertion companions

For live streams, the SDK starts an AdsWizz session and plays the stream URL the AdsWizz SDK
decorates (adding the session and listener parameters). When the stream reaches a server-side ad
break with a companion, `hasCompanionAdPublisher` goes `true` and any visible
`SDKCompanionBannerView` renders the 300×250 creative (zone 2459). It stays 5 s after the break, then
hides.

The AdsWizz SDK is included in the package (see [Getting started](getting-started.md#13-about-the-adswizz-sdk)), and
there's nothing to configure in the host app. The AdsWizz installation ID, player ID, ad server and zone are compiled into the SDK (see [Configuration](configuration.md)).

## 4. How ad types are sequenced

- An IMA preroll always wins: banners don't load while it plays, and a new item's first banner waits
  until its preroll has resolved (completed, skipped or failed), up to 12 s.
- During an IMA ad, the companion slot shows the IMA companion if there is one; otherwise the radio
  layout keeps its artwork.
- An AdsWizz companion shows in the companion slot during server-side ad breaks, independent of IMA.
- In the built-in player, nothing loads into an area hidden by full-screen video.

## 5. Consent & measurement

The SDK doesn't collect or forward consent itself (Android forwards IAB TCF strings explicitly). Use
a consent management platform, such as Google's User Messaging Platform (already in your dependency
graph through Google Mobile Ads), to collect consent before ads load. The CMP writes the standard IAB
TCF values to `UserDefaults`, which Google's ad SDKs read.

Open Measurement viewability for IMA is handled by IMA itself; register overlays you add on top of
the video with `IMAConfig.addFriendlyObstruction`.

## Ad views reference

### `SDKBannerAdView`

| API | |
|---|---|
| `init(frame:)` | |
| `onContentStateChanged: ((Bool) -> Void)?` | `true` while an ad (or the gap waveform) is showing |
| `isSuppressed: Bool` | Stops requests and hides the banner while `true` |

### `SDKGptBannerView`

| API | |
|---|---|
| `init(adUnitId: String? = nil)` | |
| `adUnitId: String?` | Setting it (re)loads |
| `sizes: [AdSize]` | Default `[300×250, 320×50]` |
| `customTargeting: [String: String]` | Added to every request |
| `onContentStateChanged: ((Bool) -> Void)?` | |

### `SDKCompanionBannerView`

Hosts an AdsWizz `AdCompanionView`. Its visibility is the on/off signal; toggle `isHidden` from
`hasCompanionAdPublisher` and call `activateIfNeeded()` when it becomes visible.

## Observing ad state

```swift
MediaSDK.shared.adPlaybackStatePublisher
    .receive(on: DispatchQueue.main)
    .sink { state in
        switch state {
        case .requesting: showAdLoading()
        case .playing:    showAdBadge()
        case .completed, .error, .idle: hideAdBadge()
        default: break
        }
    }
    .store(in: &cancellables)

MediaSDK.shared.adCountdownPublisher       // seconds left in the current ad
MediaSDK.shared.hasCompanionAdPublisher    // a companion is showing
```
