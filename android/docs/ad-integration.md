# Advertising

Ads are driven by the backend. You don't call an API to show an ad. You configure ad tags for a
station in the Atunwa dashboard, they arrive in that station's `PlayerConfig`, and the built-in
surfaces (`SDKLiveCardView`, `SDKPlayerActivity`) render them. If a station has no ad tags, it
shows no ads, and nothing changes in your code.

| Ad type | Source | Config field | Renders in | Applies to |
|---|---|---|---|---|
| **Preroll / midroll** (audio or video) | Google IMA (VAST/VMAP) | `PlayerConfig.adUrl` → `MediaItem.imaUrl` | The video surface, with IMA's own UI | Any station |
| **VAST companion** | Bundled in the IMA response | *(same tag)* | Live card (full card) and player (300×250 audio, 320×50 video) | Mostly audio prerolls |
| **Display banner** | Google Ad Manager + Amazon APS | `PlayerConfig.gptUrl` → `MediaItem.gptUrl` | `SDKBannerAdView` slots in the live card and the radio layout | Audio stations in the built-in views |
| **SSI companion** | AdsWizz server-side insertion | *(automatic for live streams)* | `SDKCompanionBannerView` (300×250) in the player's radio layout | Live streams that go through AdsWizz |

- [Setup checklist](#setup-checklist)
- [1. IMA prerolls & VAST companions](#1-ima-prerolls--vast-companions)
- [2. Display banners (Ad Manager + Amazon APS)](#2-display-banners-ad-manager--amazon-aps)
- [3. AdsWizz server-side insertion companions](#3-adswizz-server-side-insertion-companions)
- [4. How ad types are sequenced](#4-how-ad-types-are-sequenced)
- [5. Consent & measurement](#5-consent--measurement)
- [Ad views reference](#ad-views-reference)
- [Observing ad state](#observing-ad-state)

---

## Setup checklist

| To enable | You do |
|---|---|
| IMA prerolls and companions | Nothing. Set the station's IMA tag in the dashboard. |
| Ad Manager banners | Add `com.google.android.gms.ads.APPLICATION_ID` to your manifest ([Getting started §2](getting-started.md#required-only-for-ad-manager-banners)), and set the station's GPT ad unit in the dashboard. |
| Amazon APS bidding | Nothing in your app. `AMAZON_APP_ID` is compiled into the SDK when it's published ([Configuration](configuration.md#sdk-keys-set-when-the-sdk-is-published)). |
| AdsWizz SSI companions | Nothing. The AdsWizz SDK comes with the SDK, and the installation and zone IDs are already configured. |
| Consent | Use an IAB TCF v2 CMP that writes `IABTCF_TCString` to the default SharedPreferences. |

---

## 1. IMA prerolls & VAST companions

When a loaded `MediaItem` has an `imaUrl`, the SDK attaches it as a Media3 ads configuration.
ExoPlayer's IMA extension then requests the ad and plays it before content, or at its scheduled
midroll points:

```
load(item with imaUrl)
  adPlaybackState: Idle → Requesting → Loaded → Playing → Completed
  state:           LOADING → AD_PLAYING → PLAYING
  adCountdown:     counts down seconds remaining while Playing
```

- Content pauses during the ad and resumes automatically afterwards.
- IMA's own UI renders in the SDK's video surfaces: ad attribution, countdown and the skip button.
- If the ad fails (`AdPlaybackState.Error`), content starts anyway.
- Some live and audio-only insertions never send IMA's completion event. When the player reports
  it's back on content, the SDK clears the stale ad state itself, so the UI doesn't stay stuck on
  "Ad playing".

For a custom `MediaItem`, set `imaUrl` yourself. See [Media playback](media-playback.md#a-custom-mediaitem).

### VAST companions

If the ad response includes companion creatives, IMA renders them into slots that the SDK
surfaces register through `ImaAdProvider`:

| Surface | Slot size | Container |
|---|---|---|
| `SDKLiveCardView` (audio) | 300×250 | The whole card. The creative is scaled to fit. |
| `SDKPlayerActivity`, radio layout | 300×250 | The banner slot |
| `SDKPlayerActivity`, TV layout | 320×50 | Below the video |

`MediaSDK.hasCompanionAd` becomes `true` when an ad with companions starts. If nothing has rendered
into a slot after **3 seconds**, it goes back to `false`, so the UI never keeps an empty companion
box on screen. It's cleared when the ad completes.

Several screens can register slots at once, because the live card stays mounted under the full
player. Slots are tracked per owner:

```kotlin
// Only needed if you build your own surface:
val ima = MediaSDK.imaAdProvider ?: return
val slot = ima.createCompanionAdSlot(myContainer, 300, 250)
ima.registerCompanionSlots("myScreen", listOf(slot))
// on teardown:
ima.unregisterCompanionSlots("myScreen")
```

### Tuning IMA (`IMAConfig`)

`MediaSDK.imaConfig` is read when the IMA ads loader is first built, which happens on the first
load of an item that has an `imaUrl`. Change it before then:

```kotlin
MediaSDK.imaConfig = MediaSDK.imaConfig.copy(
    language = "en",
    vastLoadTimeoutMs = 4_000,   // default 4000
    mediaLoadTimeoutMs = 5_000,  // default 5000
    maxRedirects = 4,
    enableDebugMode = false,
    adAttribution = true,        // show the "Ad" attribution label
    adCountDown = true,          // show IMA's countdown
    enableFocusSkipButton = true,
    playerType = "atunwa-media-sdk",
    playerVersion = "1.0.0",
    sessionId = null,
)
```

**Friendly obstructions.** If you overlay your own views on a video surface, such as a logo
watermark or a close button, register them so Open Measurement doesn't count them as blocking the
ad:

```kotlin
MediaSDK.imaConfig.addFriendlyObstruction(
    watermarkView,
    AdOverlayInfo.PURPOSE_NOT_VISIBLE,   // androidx.media3.common.AdOverlayInfo
    "Station watermark",
)
```

---

## 2. Display banners (Ad Manager + Amazon APS)

`SDKGptManager` starts the Google Mobile Ads SDK during `MediaSDK.setup()`, and only if the app ID
meta-data is present. It also starts Amazon APS when `AMAZON_APP_ID` is set. `SDKBannerAdView` does
the rest.

### Request flow

0. **Gate.** If the Mobile Ads SDK isn't enabled because the app ID is missing or `setup()`
   hasn't run, the view hides itself and reports no content.
1. **Size fitting.** The view measures its parent and requests only the supported sizes that fit.
   The defaults are `MEDIUM_RECTANGLE` (300×250) and `BANNER` (320×50). If none fit, it requests
   all of them.
2. **Amazon APS.** If APS is set up, it fetches bids and adds them to the `AdManagerAdRequest` as
   custom targeting. If the bid fails, it loads without APS targeting.
3. **Load** the `AdManagerAdView`. Every request carries custom targeting taken from the active
   `playerConfig`, which you can use for line-item targeting in Ad Manager:

   | Key | Value |
   |---|---|
   | `lrp_domain` | `playerConfig.authorisedDomain.url` |
   | `lrp_publisher_name` | `playerConfig.publisher.name` |
   | `lrp_play_name` | `playerConfig.playerName` |
4. **Refresh cycle.** The banner shows for a random **30–60s**, then a **10s gap**, then the next
   load. During the gap, if playback is active, a muted waveform placeholder shows that the slot is
   alive. Otherwise the slot is hidden.
5. **Ad-aware.** While an IMA ad is playing, the view never loads or refreshes.

### Lifecycle

`SDKBannerAdView` is a `DefaultLifecycleObserver`. It attaches to the nearest
`LifecycleOwner`, resumes and pauses the `AdManagerAdView` with that owner, and destroys it in
`onDestroy`. Call `destroy()` only if you remove the view before its owner is destroyed.

### Using it in your own layouts

```xml
<com.atunwadigital.mediasdk.ui.SDKBannerAdView
    android:id="@+id/banner"
    android:layout_width="match_parent"
    android:layout_height="250dp"
    app:adUnitId="/1234567/home_banner"
    app:autoLoad="true" />
```

```kotlin
val banner = findViewById<SDKBannerAdView>(R.id.banner)

// Or load programmatically, optionally restricting sizes:
banner.loadAd("/1234567/home_banner", listOf(AdSize.BANNER))

// Show a fallback whenever the slot has nothing to show (no fill, or the gap while idle):
banner.onContentStateChanged = { hasContent -> fallbackView.isVisible = !hasContent }
```

Use `onContentStateChanged` to decide whether to show fallback content. Don't base it on whether an
ad unit is configured, because a configured unit can still return no fill.

> The built-in views show GPT banners only for **audio** stations. Video stations rely on their
> preroll, and their companion slot shows IMA companions only.

---

## 3. AdsWizz server-side insertion companions

> The AdsWizz SDK is included: Atunwa redistributes it with the SDK, so there's nothing to add or
> configure in your app.

For **live** streams, AdsWizz inserts ad audio into the stream itself on the server. The SDK's job
is to make the ad server serve breaks that carry companions, and to show the companion banner while
a break plays. Three parts make that work:

1. **SDK bootstrap.** `AdsWizzSDKManager` runs during `setup()`. It initializes `AdswizzSDK` with
   the installation and player IDs, configures the companion zone (AFR host, path and zone 2459),
   and sets the companion options, including a 5s extra exposure.
2. **URL decoration.** On every `load()` of a `StreamType.LIVE` item, `AdsWizzStreamCoordinator`
   asks the SDK to decorate the stream URL with session parameters, and the decorated URL is the
   one that plays. An undecorated URL still plays audio, but the server won't serve companion
   breaks for it.
3. **Metadata bridge.** `CoreMediaService` forwards the stream's ID3 metadata to AdsWizz through
   `AdsWizzPlayerBridge`, an `AdStreamPlayerAdapter`. AdsWizz uses that metadata to detect ad
   breaks. The SDK keeps its own ExoPlayer. AdsWizz observes it and doesn't control playback.

During a break:

```
adBreakStarted ─► any ad hasCompanion? ─► hasCompanionAd = true ─► SDKCompanionBannerView shown
adBreakEnded   ─► wait 5s (extra exposure) ─► hasCompanionAd = false ─► slot hidden
```

`SDKCompanionBannerView` wraps AdsWizz's `AdCompanionView`. The AdsWizz SDK fills it, and its
visibility is the signal AdsWizz watches to start and stop rendering. Several instances on screen
render the same creative.

**Failure policy: playback always wins.** If the stream manager can't be built, `play()` throws, or
no decorated URL arrives within **5 seconds**, the SDK plays the plain URL without companions.
Every new `load()` or `reset()` tears down the previous AdsWizz session.

To show SSI companions in your own screen, add the view and toggle it from `hasCompanionAd`:

```xml
<com.atunwadigital.mediasdk.ui.SDKCompanionBannerView
    android:id="@+id/companion"
    android:layout_width="300dp"
    android:layout_height="250dp"
    android:visibility="gone" />
```

```kotlin
MediaSDK.hasCompanionAd
    .onEach { companion.isVisible = it }
    .launchIn(lifecycleScope)
```

> `hasCompanionAd` is shared by IMA companions and AdsWizz companions. In practice they don't
> overlap: IMA companions appear during a preroll, and AdsWizz companions during in-stream breaks
> of live content.

---

## 4. How ad types are sequenced

A banner request comes back much faster than an IMA ad request. Loading both at once would show
the banner and then pull it away when the preroll starts. Each surface uses an `AdSequencer` (or
equivalent logic in `SDKPlayerActivity`) to decide what the banner slot should do:

| Situation | Banner slot |
|---|---|
| An IMA ad is playing | Not touched (`ImaActive`). Companions take priority. |
| A preroll is configured but hasn't resolved yet | Held back (`AwaitingPreroll`). The wait ends when the preroll completes, is skipped or fails, or after **12 seconds**. |
| The preroll has resolved and the item has a `gptUrl` | Banner loads (`ShowGpt`) |
| No `gptUrl` | Nothing (`NoGpt`) |

When the active item changes, any banner left over from the previous item is cleared.

`AdSequencer` is public, so you can reuse it in a custom screen:

```kotlin
val sequencer = AdSequencer()   // one per screen

combine(MediaSDK.currentMediaItemFlow, MediaSDK.adPlaybackState, MediaSDK.state, ::Triple)
    .onEach { (item, adState, state) ->
        item ?: return@onEach
        val outcome = sequencer.decide(item, adState, state)
        if (outcome.itemChanged) banner.clearGptBannerContent()
        when (val d = outcome.decision) {
            is AdSequencer.Decision.ShowGpt -> banner.loadAd(d.gptUrl)
            AdSequencer.Decision.NoGpt -> banner.isVisible = false
            AdSequencer.Decision.AwaitingPreroll, AdSequencer.Decision.ImaActive -> Unit
        }
    }
    .launchIn(lifecycleScope)
```

---

## 5. Consent & measurement

- **IAB TCF v2.** `CoreMediaService` reads `IABTCF_TCString` from the default SharedPreferences
  (`<package>_preferences`) for every stream request. If the string is present, it appends
  `tcf=<string>`, unless the URL already has a `tcf=` parameter. Use a TCF-compliant CMP, such as
  Google UMP, that writes the standard keys. The SDK doesn't show a consent UI.
- **Ad Manager and IMA consent.** The Google SDKs read the same IAB keys themselves.
- **Open Measurement.** OMID viewability works through the IMA and Google Mobile Ads SDKs.
  Register overlays as [friendly obstructions](#tuning-ima-imaconfig).

---

## Ad views reference

### `SDKBannerAdView`

| Member | Description |
|---|---|
| `app:adUnitId` (XML) | Ad Manager ad unit path, such as `/1234567/home_banner`. |
| `app:autoLoad` (XML, default `false`) | Load `adUnitId` automatically when attached to a window. |
| `loadAd(adUnitId, sizes = [300x250, 320x50])` | Load an ad. A blank ID hides the view and reports no content. |
| `refreshAd()` | Reload the current unit. Skipped while an IMA ad is playing. |
| `clearGptBannerContent()` | Remove the GPT ad but keep the container, so IMA companions can still render there. |
| `onContentStateChanged: ((Boolean) -> Unit)?` | Whether the slot has visible content: an ad, or the gap placeholder. |
| `destroy()` | Release resources. Called automatically when the lifecycle owner is destroyed. |

### `SDKCompanionBannerView`

A `FrameLayout` that hosts an AdsWizz `AdCompanionView` filling its bounds. It has no public API.
Add it to the layout and control its visibility. Size it to the zone's creative, 300×250dp.

---

## Observing ad state

```kotlin
MediaSDK.adPlaybackState.onEach { ad ->
    when (ad) {
        AdPlaybackState.Requesting -> status.text = "Requesting ad…"
        AdPlaybackState.Loaded     -> status.text = "Loading ad…"
        AdPlaybackState.Playing    -> status.text = "Ad playing"
        AdPlaybackState.Completed,
        AdPlaybackState.Idle       -> status.text = ""
        is AdPlaybackState.Error   -> Log.w("Ads", "IMA error", ad.throwable)
        else -> Unit
    }
}.launchIn(lifecycleScope)

MediaSDK.adCountdown.onEach { secs -> countdown.text = "${secs}s" }.launchIn(lifecycleScope)
MediaSDK.hasCompanionAd.onEach { companionSlot.isVisible = it }.launchIn(lifecycleScope)
```

Useful log tags for ads: `AtunwaAdsDebug`, `ImaAdProvider`, `SDKBannerAdView`, `SDKGptManager`,
`AdsWizzSDKManager`, `AdsWizzStreamCoordinator`.
