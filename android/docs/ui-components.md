# UI components

All views live in `com.atunwadigital.mediasdk.ui`. They fall into three groups:

| Group | Views | Binding |
|---|---|---|
| **Drop-in screens and widgets** | `SDKLiveCardView`, `SDKMiniPlayerView`, `SDKAudioPlayerView`, `SDKPlayerActivity` | Fully reactive. They observe `MediaSDK` and `SDKThemeManager` on their own. |
| **Playback surfaces** | `SDKVideoView` | Attach once with `MediaSDK.attachVideoView(...)` |
| **Ad slots** | `SDKBannerAdView`, `SDKCompanionBannerView` | Self-managing. See [Advertising](ad-integration.md). |
| **Building blocks** | `SDKWaveformView`, `SDKProgressBar`, `SDKControlButton`, `SDKControlsView`, `SDKShakeMeView`, `LiveBadgeAnimator` | Manual. You drive them yourself. |

- [SDKLiveCardView](#sdklivecardview)
- [SDKMiniPlayerView](#sdkminiplayerview)
- [SDKPlayerActivity (built-in full-screen player)](#sdkplayeractivity-built-in-full-screen-player)
- [SDKAudioPlayerView](#sdkaudioplayerview)
- [SDKVideoView](#sdkvideoview)
- [Ad views](#ad-views)
- [Building blocks](#building-blocks)
- [A typical home screen](#a-typical-home-screen)

---

## SDKLiveCardView

The "now playing" card for a home screen. It shows station branding, a live waveform for audio,
live video for TV stations, a LIVE or AD badge, a status line and a play/pause button. Its height
always follows its width at **16:9**, and any `layout_height` you set is ignored.

```xml
<com.atunwadigital.mediasdk.ui.SDKLiveCardView
    android:id="@+id/liveCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

| API | Purpose |
|---|---|
| `var onCardClickListener: (() -> Unit)?` | Called when the card body is tapped. You decide what "open" means. |
| `var previewPlayer: PlayerConfig?` | A station to show before anything is loaded. It's also the station the play button cold-starts. `MediaSDK.playerConfig` takes priority over it once something is active. |

```kotlin
val liveCard = findViewById<SDKLiveCardView>(R.id.liveCard)
liveCard.previewPlayer = MediaSDK.publisherConfig?.players?.firstOrNull()
liveCard.onCardClickListener = {
    if (MediaSDK.playerConfig != null) MediaSDK.launchPlayer(this)
    else liveCard.previewPlayer?.let { MediaSDK.loadPlayer(this, it) }
}
```

The card handles these on its own:

- **Play/pause never navigates.** The button toggles `play()` and `pause()`. If nothing is loaded,
  it calls `MediaSDK.activatePlayer(context, previewPlayer)`, which starts the station in place.
  Only a tap on the card body calls your listener.
- **Branding.** It shows the station logo in a soft, brand-tinted chip, the station name and the
  publisher name. Colors come from `PlayerConfig.colors`, falling back to the app theme and then to
  the default blue. See [Theming](theming.md).
- **Video.** For video stations it renders the live video, including IMA video ads, through an
  internal surface that has no controls. When `SDKPlayerActivity` is open in full screen or PiP
  (`MediaSDK.isDedicatedPlayerActive`), the card gives up the surface and reclaims it when the
  activity closes.
- **Ads (audio stations).** It shows a GPT banner on the Ad Manager refresh cycle once any
  preroll has resolved, and gives an audio preroll's VAST companion the full card area, scaled to
  fit without distortion. Video stations don't fetch a separate banner.
- **Status.** The label reads "Buffering…", "Requesting ad…", "Loading ad…", "Ad playing" or
  "Playing live". The button is disabled, but still shows the pause icon, while the stream is
  buffering or an ad is requesting or playing.
- **Chrome.** The branding row and waveform shrink during video or an ad, so the content is the
  focus.
- **Lifecycle.** It registers its IMA companion slot when attached to a window and unregisters it
  when detached.

## SDKMiniPlayerView

A persistent bottom bar for audio. It shows artwork, title and subtitle, play/pause, a close
button, and a thin progress line for VOD. A tap or an upward swipe expands it. Taps on its own
buttons don't trigger the expand gesture.

```xml
<com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
    android:id="@+id/miniPlayer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:visibility="gone" />
```

| API | Purpose |
|---|---|
| `var onExpandListener: (() -> Unit)?` | Called on a tap or an upward swipe. Usually opens the full player. |
| `var onCloseClickListener: (() -> Unit)?` | Called when the close button is tapped. Usually calls `MediaSDK.reset()`. |

```kotlin
miniPlayer.onExpandListener = { MediaSDK.launchPlayer(this) }
miniPlayer.onCloseClickListener = { MediaSDK.reset() }

// You control visibility. Show it for audio only, while something is loaded.
MediaSDK.state
    .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
    .onEach { state ->
        val isAudio = MediaSDK.currentMediaItem?.streamChannels?.isVideo == false
        miniPlayer.isVisible = state != SDKState.IDLE && isAudio
    }
    .launchIn(lifecycleScope)
```

Play/pause state, metadata, theme colors and progress all update internally.

## SDKPlayerActivity (built-in full-screen player)

A complete player screen that's declared in the SDK manifest. You don't add it to a layout. You
launch it:

```kotlin
MediaSDK.launchPlayer(context)                    // show whatever is active
MediaSDK.launchPlayer(context, apiKey)            // single-player: activate + show
MediaSDK.loadPlayer(context, playerConfig)        // publisher: activate + show
```

It provides:

| Feature | Detail |
|---|---|
| **Adaptive layout** | A **TV layout** with a video surface and transport controls when `streamChannels.isVideo` is true. Otherwise a **radio layout** with a spinning disc, waveform and station art. |
| **Full-screen video** | Video stations open **full screen in landscape** by default: the video fills the screen and the system bars are hidden (swipe from an edge to reveal them). A button in the video controls switches to the portrait layout, with stream info, related channels and banners below the video, and back again. With system auto-rotate on, turning the device does the same: landscape enters full screen and portrait exits it. Right after the player opens or the button is tapped, rotation is ignored until the device is held the way the current mode expects, so it never undoes that choice straight away. While the video is full screen, the below-video GPT banner isn't requested and the 320×50 IMA video companion slot isn't offered to IMA, so no impressions are counted on hidden ads. The change applies from the next ad request if one is in progress. Back always closes the player. Set `MediaSDK.videoFullscreenByDefault = false` to open video in the portrait layout instead. |
| **Picture-in-Picture** | For **video** stations that are playing (or showing a preroll), **Back minimizes the player into a PiP window** instead of closing it (`MediaSDK.videoPipOnBack`, default `true`). Leaving the app (Home, recents) also enters PiP, automatically on API 31+. Radio, paused video and devices without PiP support close on Back as before, and never auto-enter PiP. Expanding the window restores the full player. Closing it pauses playback, so the video's audio doesn't keep playing unseen. Opening the player again from your app while a PiP window is up (for example from your station list or mini player) closes that window, so there's only ever one player. Set `videoPipOnBack = false` if Back should return to your app, for example where `SDKLiveCardView` takes the video back. A station switch elsewhere (`dismissPlayerSignal`) closes the old PiP window. |
| **Cast** | A media route button. Playback moves to the Cast device, keeping the current position. |
| **Quality selection** | A picker for video and audio tracks, with a badge showing the current quality. |
| **Share** | Shares the station title, show name and stream URL through the system share sheet. |
| **Related channels** | In publisher mode, video stations show a rail of the publisher's other video stations. |
| **Ads** | IMA prerolls render in the surface, GPT banners appear in the radio layout's ad slot (replacing the disc while a banner is showing), IMA VAST companions (300×250 for audio, 320×50 for video) and AdsWizz SSI companions (300×250) render too. See [Advertising](ad-integration.md). |
| **Loading states** | Skeleton pulse while loading, with buffering and ad-aware indicators. Controls are disabled during ads. |

The activity uses `launchMode="singleTop"` and handles configuration changes itself, so rotation
doesn't interrupt playback. It's themed with `Theme.MaterialComponents.DayNight.NoActionBar`.

## SDKAudioPlayerView

A self-contained, large-format audio player view: title, subtitle, a progress bar (VOD only) and a
themed play/pause button. Use it when you want an embedded "now playing" panel instead of the
full-screen activity.

```xml
<com.atunwadigital.mediasdk.ui.SDKAudioPlayerView
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

It binds itself to `SDKThemeManager.theme`, `MediaSDK.state`, `currentMediaItemFlow` and
`playWhenReady` when attached to a window, and stops observing when detached.

## SDKVideoView

A Media3 `PlayerView` wrapper with the SDK's own overlay controls: rewind and forward, play/pause,
a progress bar that disables seeking during ads, a loader, and tap to show or hide the controls. It
uses a `TextureView` surface, so moving playback between views doesn't flash black.

```xml
<com.atunwadigital.mediasdk.ui.SDKVideoView
    android:id="@+id/videoView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

```kotlin
MediaSDK.attachVideoView(findViewById(R.id.videoView))
// …or create one programmatically:
container.addView(MediaSDK.createVideoView(context))
```

| API | Purpose |
|---|---|
| `attachPlayer(player)` | Called for you by `attachVideoView` and `createVideoView`. |
| `setShowLoader(Boolean)` / `setLoaderTint(Int)` | Show the loading spinner and set its tint. |
| `setAccentColor(Int)` | Accent color for the progress bar and controls. |

Attach it only after `MediaSDK.setup()` has completed. Before that, `mediaController` is `null` and
nothing is attached.

## Ad views

These are covered fully in [Advertising](ad-integration.md#ad-views-reference):

- **`SDKBannerAdView`**: a Google Ad Manager banner with Amazon APS bidding, size fitting,
  refresh every 30–60s, and lifecycle handling. XML attributes: `app:adUnitId`, `app:autoLoad`.
- **`SDKCompanionBannerView`**: a 300×250 host for AdsWizz SSI companion creatives.

## Building blocks

Use these when you build fully custom UI. Unlike the views above, they **don't** observe `MediaSDK`
themselves. You drive them.

### SDKWaveformView

A five-bar animated audio visualizer with a gradient in the brand colors and a buffering shimmer.

```kotlin
waveform.setBarColor(primary = brandColor, highlight = accentColor)

MediaSDK.state.onEach { s ->
    waveform.setBuffering(s == SDKState.BUFFERING)
    if (s == SDKState.PLAYING) waveform.startAnimation() else waveform.stopAnimation()
}.launchIn(lifecycleScope)

// Optional: drive the bars from real audio (requires RECORD_AUDIO, see getting-started.md)
MediaSDK.amplitude.onEach(waveform::updateAmplitude).launchIn(lifecycleScope)
```

### SDKProgressBar

A seek bar with elapsed and total time labels.

| API | Purpose |
|---|---|
| `setProgress(positionMs, durationMs, bufferedMs = 0)` | Update the position. Call it from a ticker. |
| `setLive(Boolean)` | Live mode turns off seeking and hides the duration label. |
| `var onSeekListener: ((Long) -> Unit)?` | Called when the user releases the thumb, with the target in ms. |
| `var onDraggingListener: ((Boolean) -> Unit)?` | Reports when a drag starts and ends. |
| `setAccentColor`, `setTextColor`, `showTimeTexts` | Styling. |

```kotlin
progress.onSeekListener = { ms -> MediaSDK.mediaController?.seekTo(ms) }
lifecycleScope.launch {
    while (isActive) {
        MediaSDK.mediaController?.let { progress.setProgress(it.currentPosition, it.duration, it.bufferedPosition) }
        delay(500)
    }
}
```

### SDKControlButton and SDKControlsView

- `SDKControlButton` is a styled `AppCompatImageView` with `setIcon(resId)` and
  `setIconTint(color)`. It has no playback logic, so wire its click listener yourself.
- `SDKControlsView` is an overlay container with a centered row (`addCenterControl(view)`), a
  bottom stack (`addBottomControl(view)`), `showControls(Boolean)`, and callback slots
  (`onPlayPauseClick`, `onRewindClick`, `onForwardClick`, `onNextClick`, `onPrevClick`) for you to
  invoke.

The SDK ships these icon drawables for building controls: `sdk_ic_play`, `sdk_ic_pause`,
`sdk_ic_rewind_10`, `sdk_ic_rewind_30`, `sdk_ic_forward_10`, `sdk_ic_forward_30`, `sdk_ic_next`,
`sdk_ic_prev`, `sdk_ic_live`, `sdk_ic_share`, `sdk_ic_close` and `sdk_ic_loading`.

### LiveBadgeAnimator

A "breathing" scale and alpha pulse for a LIVE badge `TextView`:

```kotlin
val pulse = LiveBadgeAnimator(liveBadge)
// state == PLAYING && isLive → pulse.startPulse()   (idempotent)
// any other state            → pulse.stopPulse()
// onDestroy                  → pulse.release()
```

### SDKShakeMeView

A placeholder badge that reads "Shake to interact", reserved for a future interactive-ad feature.
In v1.0 it has no behavior.

---

## A typical home screen

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <com.atunwadigital.mediasdk.ui.SDKLiveCardView
                android:id="@+id/liveCard"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

            <!-- your station list, news feed, etc. -->

            <com.atunwadigital.mediasdk.ui.SDKBannerAdView
                android:id="@+id/homeBanner"
                android:layout_width="match_parent"
                android:layout_height="250dp"
                app:adUnitId="/1234567/home_banner"
                app:autoLoad="true" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>

    <com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
        android:id="@+id/miniPlayer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:visibility="gone" />
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```
