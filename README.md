# Atunwa Media Player SDKs

Add Atunwa live radio and TV to your **iOS** and **Android** apps in a few lines. The SDKs fetch your
stations from the Atunwa dashboard, play them with background audio and lock-screen controls, and
show ready-made player screens with the ads you've configured, so you don't write any playback or
ad code.

Both SDKs share the same concepts and API shape: what you learn on one platform carries over to the
other.

| Platform | Install | Guide |
|---|---|---|
| **iOS** 15+ | Swift Package Manager | [iOS SDK →](ios/README.md) |
| **Android** 7.0+ (API 24) | Gradle (Maven) | [Android SDK →](android/README.md) |
| **Flutter** | The iOS and Android SDKs, plus a bridge you copy into your app | [Flutter guide →](flutter/README.md) |
| **React Native** | The iOS and Android SDKs, plus a native module you copy into your app | [React Native guide →](react-native/README.md) |

## What you get

| | |
|---|---|
| **Playback** | Live and on-demand HLS streams, background audio, lock-screen and system media controls, recovery from network drops, AirPlay (iOS) and casting |
| **Player screens** | A live card, a mini player, and a full-screen player with landscape full-screen video, rotate-to-fullscreen, Picture-in-Picture, a quality picker, sharing and related channels |
| **Station setup** | Station details, streams, branding and ad settings come from the Atunwa dashboard, and are cached so your app can start offline |
| **Ads** | Google IMA prerolls with companions, Google Ad Manager banners and AdsWizz companion banners, all driven by the dashboard |
| **Theming** | Your app's colors, plus each station's logo |
| **News** | The publisher's news feeds, aggregated |

## Before you start

You need a key from the Atunwa dashboard. Both kinds are safe to ship in your app:

- a **player API key** to play one station, or
- a **publisher SDK key** to offer all of a publisher's stations.

## Quick start: iOS

In Xcode, choose **File → Add Package Dependencies…**, enter this repository's URL and add the
**MediaSDK** library:

```
https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git
```

Enable **Signing & Capabilities → Background Modes → Audio, AirPlay, and Picture in Picture**.
Then open the player from any view controller:

```swift
import MediaSDK

MediaSDK.shared.launchPlayer(apiKey: "YOUR_PLAYER_API_KEY", from: self)
```

Next: [iOS Getting started](ios/docs/getting-started.md).

## Quick start: Android

Add this repository's Maven repository and the SDK:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven")
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.atunwadigital:mediasdk:1.0.4") // latest version: see Releases
}
```

Set the SDK up once at launch, then open the player:

```kotlin
MediaSDK.setup(applicationContext)                    // in a coroutine, e.g. from your splash screen
MediaSDK.launchPlayer(this, "YOUR_PLAYER_API_KEY")    // from an Activity
```

The SDK's own manifest already declares its service, player screen and permissions. Next:
[Android Getting started](android/docs/getting-started.md).

## Guides

| Topic | iOS | Android |
|---|---|---|
| Installation, setup and requirements | [Getting started](ios/docs/getting-started.md) | [Getting started](android/docs/getting-started.md) |
| One station or a whole publisher | [Integration modes](ios/docs/integration-modes.md) | [Integration modes](android/docs/integration-modes.md) |
| Playing, pausing and observing state | [Media playback](ios/docs/media-playback.md) | [Media playback](android/docs/media-playback.md) |
| Live card, mini player, full-screen player | [UI components](ios/docs/ui-components.md) | [UI components](android/docs/ui-components.md) |
| Prerolls, banners and companions | [Advertising](ios/docs/ad-integration.md) | [Advertising](android/docs/ad-integration.md) |
| Colors and branding | [Theming](ios/docs/theming.md) | [Theming](android/docs/theming.md) |
| News feeds | [News](ios/docs/news.md) | [News](android/docs/news.md) |
| Keys and settings | [Configuration](ios/docs/configuration.md) | [Configuration](android/docs/configuration.md) |
| Every public type and method | [API reference](ios/docs/api-reference.md) | [API reference](android/docs/api-reference.md) |
| Common problems | [Troubleshooting](ios/docs/troubleshooting.md) | [Troubleshooting](android/docs/troubleshooting.md) |

## Flutter and React Native

There are no Flutter or React Native packages yet. You can still use the SDKs from either
framework: add the native iOS and Android SDKs to your app's `ios/` and `android/` projects, then
copy in a small bridge so Dart or JavaScript can launch the player, control playback and observe
state. Playback, the player screens and ads all stay native.

| | Flutter | React Native |
|---|---|---|
| Overview and the bridge's API | [Flutter](flutter/README.md) | [React Native](react-native/README.md) |
| Native setup and the bridge code | [Getting started](flutter/docs/getting-started.md) | [Getting started](react-native/docs/getting-started.md) |
| Live card and mini player in your screens | [Native views](flutter/docs/native-views.md) | [Native views](react-native/docs/native-views.md) |
| Expo apps | | [Expo](react-native/docs/expo.md) |
| Common problems | [Troubleshooting](flutter/docs/troubleshooting.md) | [Troubleshooting](react-native/docs/troubleshooting.md) |

## Versions

Every release is listed under
[Releases](https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/releases) with
its notes and an install snippet. The two platforms are versioned separately, and the guides here
always describe each platform's latest release.

- **iOS** releases are plain version tags, such as `1.1.0`. Swift Package Manager resolves them
  directly; upgrade with **File → Packages → Update to Latest Package Versions**. Each release's
  notes say which Xcode built it: your app needs that Xcode version or newer.
- **Android** releases are tagged `android-<version>`, such as `android-1.0.4`. Upgrade by changing
  the version in your `build.gradle.kts`.

## About the AdsWizz SDK

Both SDKs include the AdsWizz SDK, which powers AdsWizz companion banners on live streams. You
don't need an AdsWizz account, and you shouldn't add the AdsWizz SDK to your app yourself.

The AdsWizz SDK is proprietary software of AdsWizz Inc., redistributed here unmodified by Atunwa
Digital for use with the Atunwa Media SDK. It isn't open source; see the notices for
[iOS](ios/third-party/ADSWIZZ-NOTICE.md) and
[Android](https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/blob/maven/com/adswizz/NOTICE.md).

## Getting help

Start with the Troubleshooting guide for your platform
([iOS](ios/docs/troubleshooting.md), [Android](android/docs/troubleshooting.md)). Both SDKs log
with a `MediaSDK` prefix; including those log lines makes any issue much quicker to diagnose. For
keys, dashboard settings or anything else, contact your Atunwa account manager.
