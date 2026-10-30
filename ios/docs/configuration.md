# Configuration

The iOS SDK has no `local.properties` equivalent. Settings come from three places:

1. **Compiled into the SDK** (`InternalConfig.swift`): the API base URL and the AdsWizz values.
   Change them in the SDK and cut a new release.
2. **Your app's Info.plist and capabilities**: background audio, orientations, the Ad Manager app ID.
3. **Runtime properties on `MediaSDK.shared`**: player behavior and ad tuning, set from code.

## Compiled into the SDK

| Setting | Value | Used for |
|---|---|---|
| `InternalConfig.sdkBaseURL` | `https://amp.atunwadigital.com` | `GET /sdk/v1/player` and `GET /sdk/v1/config`. Android's `ATUNWA_SDK_BASE_URL` |
| `InternalConfig.sdkCachePrefix` | `atunwa_mediasdk_` | Prefix for the configs cached in `UserDefaults` for offline start |
| `AdsWizz.installationId` | `Atunwa_iOS` | AdsWizz SDK initialization. Issued by AdsWizz |
| `AdsWizz.playerId` | `Atunwa_iOS_IDv3` | AdsWizz SDK initialization. Easy to swap with `installationId`, which fails silently |
| `AdsWizz.adServer` | `atunwa.deliveryengine.adswizz.com` | Ad-server host for SSI sessions. Bare host only; the SDK appends its own path |
| `AdsWizz.companionZoneId` | `2459` | Companion zone (300×250), same as web and Android |
| `AdsWizz.companionExtraExposureTimeSeconds` | `5` | How long a companion stays after its ad break, matching Android |

Android reads these from `local.properties` at build time. On iOS they're constants, so a change
means a new SDK version.

> Amazon APS has no compiled-in app ID on iOS. Header bidding goes through the `apsBidProvider` seam
> instead. See [Advertising](ad-integration.md#2-display-banners-ad-manager--amazon-aps).

## Host-app settings

| Setting | Where | Required? |
|---|---|---|
| `UIBackgroundModes` = `audio` | Info.plist / **Background Modes** capability | Yes. Background playback, lock screen and PiP |
| Landscape orientations | Info.plist `UISupportedInterfaceOrientations` / **Device Orientation** | For full-screen landscape video |
| `GADApplicationIdentifier` | Info.plist | Your Ad Manager app ID (**Inventory › Apps**), no AdMob account needed. Without a valid one, the SDK uses Google's sample app ID `ca-app-pub-3940256099942544~1458002511` so the app runs, and logs a warning. Set your own before you ship |
| `SKAdNetworkItems` | Info.plist | Recommended by Google when serving banners |
| Player API key / publisher SDK key | Your own code or config | Yes, one of the two |

Keep your keys wherever your app keeps configuration: a `Config.swift`, an `.xcconfig` read into
Info.plist, or a remote config. The sample app uses a `Config` enum:

```swift
enum Config {
    static let appMode: AppMode = .publisher
    static let playerKey = "…"          // Mode A
    static let publisherSdkKey = "…"    // Mode B
    static let themeConfig = SDKThemeConfig(primaryColorHex: "#0057FF", secondaryColorHex: "#FF6B00")
}
```

## Runtime properties

Set these on `MediaSDK.shared` before the player opens or the first stream loads.

| Property | Default | Effect |
|---|---|---|
| `videoFullscreenByDefault` | `true` | `SDKPlayerViewController` opens video stations full screen in landscape |
| `videoPipOnBack` | `true` | Closing the player while a video plays minimizes it into Picture-in-Picture |
| `imaConfig` | `IMAConfig()` | IMA timeouts, language, UI elements, friendly obstructions |
| `apsBidProvider` | `NoOpAPSBidProvider()` | Header-bidding targeting added to every Ad Manager banner request |

## A note on secrecy

Player API keys and publisher SDK keys are public-safe identifiers, not secrets: they unlock only
read-only station config. It's fine for them to ship inside the app. Don't embed anything that is a
secret, such as a directory API key or a GitHub token.

## CI/CD

The public package needs no credentials, so CI builds work as they are.
