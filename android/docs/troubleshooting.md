# Troubleshooting

## Build & setup

| Symptom | Cause / fix |
|---|---|
| `Could not resolve com.atunwadigital:mediasdk:<version>` | The Atunwa Maven repository (`https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven`) isn't in `settings.gradle.kts`, or the version doesn't exist (check the `android-…` releases in [atunwadigital/Atunwa-Media-Player-Integration-Guide](https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/releases)). |
| `Could not resolve com.adswizz:adswizz-sdk:8.2.10` | The Atunwa repository is missing from `settings.gradle.kts`, or its `content { }` filter doesn't include the `com.adswizz` group. |
| `Serializer for class 'PlayerConfig' is not found` (release builds) | R8 removed the serializers. Add the [keep rules](getting-started.md#6-code-shrinking). |
| Crash on start mentioning `MobileAdsInitProvider` / a missing app ID | Your manifest re-adds the provider or removes the SDK's `AD_MANAGER_APP` meta-data, or an older SDK copy is in use. |

## Playback

| Symptom | Cause / fix |
|---|---|
| Nothing plays, log shows `load() called before initialization — no playerConfig set.` | You called `load()` with no active station. Call `initialize()` (not just `preload()`), or use `launchPlayer`/`loadPlayer`. |
| `load(mediaItem) called before initialization.` | `setup()` hasn't completed, or you called `reset()` (which clears `isInitialized`). Wait for `setup()` before loading. |
| `state` stuck in `ERROR` after `initialize` | The config fetch failed after 3 retries and no copy was persisted on disk. Check the key, network and `ATUNWA_SDK_BASE_URL`. Look for `fetchSinglePlayerConfig(...) failed after 3 retries: HTTP 4xx`. |
| `Invalid state transition: X → Y` warnings | Harmless. The state machine ignored an update that isn't allowed from the current state. See the [transition table](media-playback.md#the-state-machine). |
| Live stream takes up to 5s longer to start | AdsWizz URL decoration timed out (`no decorated URL after 5000ms`). The stream plays undecorated, without companions. |
| Video is black on the live card while the full player is open | Expected. Only one surface shows video at a time. The card takes it back when `SDKPlayerActivity` closes. |
| Playback stops when the app is in the background | Make sure nothing in your app calls `pause()`/`reset()` in `onStop`. `CoreMediaService` keeps playing in the foreground. Check whether a battery optimizer is killing the app. |
| Waveform animates but doesn't follow the audio | `RECORD_AUDIO` hasn't been granted. See [Getting started](getting-started.md#about-record_audio). |

## Ads

| Symptom | Cause / fix |
|---|---|
| `Google Mobile Ads isn't configured (no AD_MANAGER_APP or APPLICATION_ID meta-data). Banners will be disabled.` | Your manifest removes the SDK's `com.google.android.gms.ads.AD_MANAGER_APP` meta-data (for example with `tools:node="remove"`). Drop that override. |
| `Ads are not enabled or SDK not initialized. Cannot load banner.` | `setup()` hasn't run, or Mobile Ads is still initializing. |
| Banner never appears on a video station | By design. The built-in views show banners only for audio stations. |
| Banner appears a while after playback starts | By design. The banner waits for the preroll to resolve, for up to 12s. See [sequencing](ad-integration.md#4-how-ad-types-are-sequenced). |
| Banner briefly disappears every 30–60s | That's the 10s no-ad gap between refreshes. |
| No preroll | The station has no `adUrl`, or the ad was a no-fill. Filter on `AtunwaAdsDebug` for `IMA Ad Error [...]`. |
| `Companion ad reported but did not render within timeout; falling back` | IMA said it had a companion, but nothing rendered within 3s. Usually the creative size doesn't match the 300×250 or 320×50 slot. |
| AdsWizz companion never shows | The stream must be `LIVE` and go through AdsWizz. Check `AdsWizzStreamCoordinator` logs for `stream manager build failed` or a decoration timeout, and `AdsWizzSDKManager` for `AdswizzSDK initialized`. |
| UI stuck on "Ad playing" | It recovers on its own when the player reports content again (`clearing stale ad state`). If it doesn't, file a bug with logs. |

## Log tags

Filter Logcat by these tags:

```
MediaSDK  CoreMediaService  MediaSDK_Telemetry
AtunwaAdsDebug  SDKBannerAdView  SDKGptManager
AdsWizzSDKManager  AdsWizzStreamCoordinator  AdsWizzPlayerBridge  SDKCompanionBannerView
NewsFeedFetcher
```

```bash
adb logcat -s MediaSDK CoreMediaService AtunwaAdsDebug SDKBannerAdView AdsWizzStreamCoordinator
```
