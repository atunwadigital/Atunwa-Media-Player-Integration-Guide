# Example apps

Small, complete apps that show how a publisher integrates the Atunwa Media SDK. There's one per
platform, and each one covers both ways of integrating:

| Mode | Use it when | Key from Atunwa | What the app shows |
|---|---|---|---|
| **Single player** (`single`) | Your app streams **one** station | Player **API key** | The station's branding, *Listen live* (opens the full-screen player) and *Play here*, then the SDK's live card and mini player |
| **Publisher** (`publisher`) | Your app offers **all** your stations | Publisher **SDK key** | Your stations as a list with a Radio/TV filter and pull-to-refresh, the SDK's live card for your first station, and the mini player |

Atunwa issues both kinds of key, and both are safe to ship in an app. Ask your Atunwa account
manager for the one you need. Each app reads its mode and key from a git-ignored file, so keys never
end up in source control.

| Platform | Folder | Built with | Key goes in |
|---|---|---|---|
| Android | [`android/`](android) | Kotlin, Jetpack Compose | `local.properties` |
| iOS | [`ios/`](ios) | Swift, SwiftUI | `Config/Atunwa.local.xcconfig` |
| Flutter (Android and iOS) | [`flutter/`](flutter) | Dart, plus the bridge from the [Flutter guide](../flutter/README.md) | `atunwa.json` |
| React Native (Android and iOS) | [`react-native/`](react-native) | TypeScript, plus the module from the [React Native guide](../react-native/README.md) | `atunwa.config.json` |

For a full publisher app built around the SDK (home, radio, TV with catch-up, news and podcasts
tabs, with one mini player across them), see [Media House](media-house/README.md), for Android and
iOS.

Each folder's README says how to set the key and run the app. To understand how an app is
built, and what else you could build on it, read its guide in [docs/](docs/README.md). With no key set, the apps open on a
screen that says what to add.

## What each mode calls

**Single player**

1. `initialize(apiKey)` fetches the station and makes it the active station, so its name and logo
   can be shown before anything plays.
2. `load()` starts its stream, and `launchPlayer()` opens the SDK's full-screen player.

If you don't need the station's details before playback starts, one call does both:
`launchPlayer(apiKey)`.

> After `initialize()`, the station counts as active, so `launchPlayer(apiKey)`, `loadPlayer` and
> the live card's play button all assume it's already playing and don't start the stream. Call
> `load()` to start it, as these apps do.

**Publisher**

1. `initializePublisher(sdkKey)` fetches the publisher and its stations. It doesn't start anything.
2. `loadPlayer(station)` switches to a station and opens the full-screen player.
   `activatePlayer(station)` switches to it and plays in place.

**Both:** the mini player's close button calls `reset()`, which also clears the active station and
the publisher config. The apps initialize again (from the SDK's cache, so it's instant) before the
next play.

## Notes

- **Ad Manager app ID (iOS).** The apps don't set `GADApplicationIdentifier`, so the iOS SDK (1.0.6
  and later) falls back to Google's sample app ID and logs a warning. Add your own Ad Manager app ID
  to `Info.plist` before you ship.
- **CardView (Android).** `SDKLiveCardView` extends `androidx.cardview.widget.CardView`, which the
  SDK only declares as a runtime dependency. The Android, Flutter and React Native apps add
  `androidx.cardview:cardview:1.0.0` so their Kotlin code compiles.
- The full guides are in the [main README](../README.md).
