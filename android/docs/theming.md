# Theming

Two layers of branding combine:

1. **App-wide theme (`SDKThemeConfig`)**: the colors you pass to `initialize`,
   `initializePublisher` or `launchPlayer`.
2. **Per-station data from the backend**: `PlayerConfig.colors`, `logo` and `publisher.tier`.

The SDK merges them into an `SDKTheme`, published on `SDKThemeManager.theme`, and every built-in
view reads from it.

## `SDKThemeConfig`

```kotlin
data class SDKThemeConfig(
    val primaryColorHex: String = "#0057FF",   // brand color: buttons, accents, card background
    val secondaryColorHex: String? = null,     // optional second brand color; enables gradient treatments
    val liveBadgeColorHex: String = "#F44336", // LIVE badge
    val loadingColorHex: String? = null,       // loader tint in the full-screen player
)
```

```kotlin
MediaSDK.launchPlayer(
    context, apiKey,
    themeConfig = SDKThemeConfig(
        primaryColorHex = "#7C3AED",
        secondaryColorHex = "#1A0066",
        loadingColorHex = "#FFFFFF",
    ),
)
```

| Call | Theme applied |
|---|---|
| `initialize(…, themeConfig)` / `launchPlayer(…, themeConfig)` | Your colors, the station's `publisher.tier` (defaulting to `STANDARD`), and the station logo |
| `initializePublisher(…, themeConfig)` | Your colors, tier `STANDARD`, and the publisher logo |
| `loadPlayer` / `activatePlayer` | Keeps the current theme and swaps in the new station's logo |

> `liveBadgeColorHex` is stored in `SDKTheme`, but the current built-in views don't read it. The
> LIVE badge styling comes from the tier.

## Per-station colors

The backend can return colors for each station:

```kotlin
data class PlayerColors(
    val primary: String? = null,
    val secondary: String? = null,
    val highlight: String? = null,
    val text: String? = null,
)
```

**`SDKLiveCardView`** prefers them over the app theme, so each of a publisher's stations keeps its
own brand:

```
primary:   PlayerConfig.colors.primary  →  SDKTheme.primaryColorHex  →  #0057FF
highlight: PlayerConfig.colors.highlight →  (resolved primary)
```

Every step is validated. A null, blank or malformed value falls through to the next one instead
of crashing. A hex value without its leading `#` (`"0057FF"`) is accepted, and named Android colors
such as `"red"` still parse.

**`SDKPlayerActivity`** and **`SDKMiniPlayerView`** use the app-wide `SDKTheme` colors.

## Pricing tier

`PricingTier` (`STANDARD` or `CUSTOM`) comes from `PlayerConfig.publisher.tier`. It switches the
full-screen player between two visual treatments:

| | `STANDARD` | `CUSTOM` |
|---|---|---|
| Radio layout | Light background, blue accents, standard disc | Branded gradient backdrop, white text, custom disc, yellow waveform and LIVE badge |
| TV layout | Black backdrop, blue quality pill | Branded backdrop and quality pill |
| Fallback primary | Metro blue | Joy red |

## Logos

The SDK uses `PlayerConfig.logo`, falling back to `publisher.logo`, wherever it shows a station
logo: the live card chip, the related-channels rail and the media notification
artwork. You don't need any API call for this. Point the field at an image URL, and Coil loads it.

## Reading the theme in custom UI

```kotlin
SDKThemeManager.theme
    .filterNotNull()
    .onEach { theme ->
        val primary = Color.parseColor(theme.primaryColorHex)
        myButton.backgroundTintList = ColorStateList.valueOf(primary)
        theme.stationLogoUrl?.let { logoView.load(it) }
    }
    .launchIn(lifecycleScope)
```

`SDKTheme` fields: `tier`, `primaryColorHex`, `secondaryColorHex`, `stationLogoUrl`,
`liveBadgeColorHex`, `loadingColorHex`.

## Day/night

The SDK ships `values/colors.xml` and `values-night/colors.xml`, and `SDKPlayerActivity` uses a
`DayNight` theme, so the light surfaces follow the system dark-mode setting.
