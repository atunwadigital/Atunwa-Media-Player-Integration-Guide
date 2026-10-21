# Theming

The SDK's views take their colors from one theme, published by `SDKThemeManager.shared`. You set the
colors once with `SDKThemeConfig`; the SDK adds the active station's logo and the publisher's pricing
tier to the published theme.

## `SDKThemeConfig`

Pass it to `initialize`, `launchPlayer(apiKey:…)`, `initializePublisher` or `launchPublisher`:

```swift
let theme = SDKThemeConfig(
    primaryColorHex: "#0057FF",      // play buttons, waveform, spinners, quality button
    secondaryColorHex: "#FF6B00",    // optional accent (SDKAudioPlayerView)
    liveBadgeColorHex: "#F44336",    // the LIVE badge
    loadingColorHex: nil             // published in the theme for your own UI
)
try await MediaSDK.shared.initializePublisher(sdkKey: PUBLISHER_SDK_KEY, themeConfig: theme)
```

| Field | Default | Used by |
|---|---|---|
| `primaryColorHex` | `#0057FF` | Play/pause buttons, waveform bars, spinners, the player's quality button and AirPlay highlight, in every SDK view |
| `secondaryColorHex` | `nil` | `SDKAudioPlayerView`'s accent (falls back to a tint of primary) |
| `liveBadgeColorHex` | `#F44336` | `SDKLiveBadgeView` everywhere |
| `loadingColorHex` | `nil` | Not used by the SDK's own views yet; available in the theme for your UI |

## Station logo

Switching station (`loadPlayer`, `activatePlayer`, `initialize`) updates only the theme's
`stationLogoUrl`, keeping your colors. In publisher mode, before any station is active, the logo is
the publisher's. The SDK's own views show the station artwork from the current `MediaItem`;
`stationLogoUrl` is there for your UI.

## Per-station colors

> Android also applies a station's own brand colors (`PlayerConfig.colors`) from the backend. The iOS
> `PlayerConfig` doesn't decode that field yet, so iOS uses the app-wide `SDKThemeConfig` colors for
> every station.

## Pricing tier

`SDKTheme.tier` (`.standard` or `.custom`) comes from the station's publisher (single-player mode) or
defaults to `.standard` (publisher mode). On Android the player switches to a custom visual
treatment for `.custom`; the iOS views don't vary by tier yet, but the value is there for your UI.

## Reading the theme in custom UI

```swift
SDKThemeManager.shared.themePublisher
    .compactMap { $0 }
    .receive(on: DispatchQueue.main)
    .sink { [weak self] theme in
        self?.playButton.tintColor = UIColor(hex: theme.primaryColorHex)
        self?.logoView.load(theme.stationLogoUrl)
    }
    .store(in: &cancellables)

let current = SDKThemeManager.shared.currentTheme   // nil until the first initialize
```

`UIColor(hex:)` is an SDK extension that parses `#RRGGBB` strings.

You can also push a theme yourself with `SDKThemeManager.shared.updateTheme(_:)`, for example to
recolor the views for a promotion. The next station switch keeps your colors and replaces only the
logo.

## Light and dark mode

SDK views use system colors (`.label`, `.systemBackground`, `.secondarySystemFill`) for everything
except the theme colors, so they follow the system appearance. The full-screen video layout is
always black.
