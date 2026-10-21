# Configuration

Keys are kept out of source code and version control. At build time, Gradle reads them from the
root project's `local.properties`, which is git-ignored, and compiles them into `BuildConfig`
fields or manifest placeholders.

There are two groups. **SDK keys** are compiled into the SDK when it's published. **App keys** are
read by your app's build. If you consume the published artifact, you only set the app keys.

## SDK keys (set when the SDK is published)

These are compiled into the SDK when Atunwa publishes it, so every app that uses a given version gets
the same values. Setting them in your app's `local.properties` has no effect.

| Key | Module | Default | Purpose |
|---|---|---|---|
| `ATUNWA_SDK_BASE_URL` | `mediasdk` | `https://amp.atunwadigital.com` | Base URL of the Atunwa SDK REST API, with no trailing slash. Config is fetched from `$base/sdk/v1/player` and `$base/sdk/v1/config`. |
| `AMAZON_APP_ID` | `mediasdk` | *(empty)* | Amazon Publisher Services app ID. When set, APS is initialized and its bids are attached to every Ad Manager banner request. When empty, APS is skipped. |
| `ADSWIZZ_PARTNER_ID` | `mediasdk` | *(empty)* | Reserved for the AdsWizz partner ID; the current integration doesn't read it. |
| `ADSWIZZ_INSTALLATION_ID` | `mediasdk` | `Atunwa_android` | AdsWizz SDK installation ID, issued by AdsWizz. |
| `ADSWIZZ_PLAYER_ID` | `mediasdk` | `Atunwa_android_IDv3` | AdsWizz player ID, issued by AdsWizz. |
| `ADSWIZZ_AFR_HOST` | `mediasdk` | `atunwa.deliveryengine.adswizz.com` | AdsWizz companion-banner (AFR) delivery host. |
| `ADSWIZZ_AFR_ENDPOINT_PATH` | `mediasdk` | `www/delivery/afr.php` | AFR endpoint path. |
| `ADSWIZZ_COMPANION_ZONE_ID` | `mediasdk` | `2459` | Companion zone ID. The creative size is 300×250dp. |

## Build keys (read by your `settings.gradle.kts`)

None. The SDK and the AdsWizz SDK it uses both come from Atunwa's public repository, with no
credentials.

## Host-app keys (read by your app module)

The SDK doesn't read these. They're the conventions the sample `:app` uses, and you can follow them
or wire your own equivalents:

| Key | Purpose |
|---|---|
| `ADMOB_APP_ID` | Google Mobile Ads app ID, passed into the manifest's `com.google.android.gms.ads.APPLICATION_ID` meta-data through `manifestPlaceholders`. Required for Ad Manager banners. |
| `ATUNWA_APP_MODE` | `single` or `publisher`, which picks the [integration mode](integration-modes.md). |
| `ATUNWA_PLAYER_KEY` | Player API key for single-player mode. |
| `ATUNWA_PUBLISHER_SDK_KEY` | Publisher SDK key for publisher mode. |

```kotlin
// app/build.gradle.kts
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    defaultConfig {
        manifestPlaceholders["ADMOB_APP_ID"] =
            localProperties.getProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"

        buildConfigField("String", "APP_MODE", "\"${localProperties.getProperty("ATUNWA_APP_MODE") ?: "single"}\"")
        buildConfigField("String", "PLAYER_KEY", "\"${localProperties.getProperty("ATUNWA_PLAYER_KEY") ?: ""}\"")
        buildConfigField("String", "PUBLISHER_SDK_KEY", "\"${localProperties.getProperty("ATUNWA_PUBLISHER_SDK_KEY") ?: ""}\"")
    }
    buildFeatures { buildConfig = true }
}
```

## Complete `local.properties` template

For an app that uses the SDK:

```properties
# ── Atunwa ────────────────────────────────────────────────────────────
ATUNWA_APP_MODE=single                 # or "publisher"
ATUNWA_PLAYER_KEY=                     # single-player mode
ATUNWA_PUBLISHER_SDK_KEY=              # publisher mode

# ── Google Ad Manager (banners) ───────────────────────────────────────
ADMOB_APP_ID=ca-app-pub-3940256099942544~3347511713   # Google's test ID; replace for production
```

## A note on secrecy

`local.properties` keeps keys out of git, but anything compiled into `BuildConfig` or the manifest
ships inside the APK and can be extracted. Player API keys and SDK keys identify a station or
publisher, and they're designed to be embedded in clients. Don't put true server secrets in any of
these fields.

## CI/CD

The SDK itself needs no credentials, so CI builds resolve it as they are. Write any app keys you use from
pipeline secrets before building:

```bash
# Example CI step (GitHub Actions, Bitrise, etc.)
cat > local.properties <<EOF
sdk.dir=$ANDROID_HOME
ADMOB_APP_ID=$ADMOB_APP_ID
ATUNWA_APP_MODE=publisher
ATUNWA_PUBLISHER_SDK_KEY=$ATUNWA_PUBLISHER_SDK_KEY
EOF

./gradlew assembleRelease
```
