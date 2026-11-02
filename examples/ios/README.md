# iOS example

A SwiftUI app using the Atunwa Media SDK for iOS. See the [examples overview](../README.md) for what
the two modes show.

| File | What it shows |
|---|---|
| [`AtunwaMediaExampleApp.swift`](AtunwaMediaExample/AtunwaMediaExampleApp.swift) | Touching `MediaSDK.shared` at launch, then the screen for the configured mode |
| [`SinglePlayerView.swift`](AtunwaMediaExample/SinglePlayerView.swift) | Single-player mode, with a player API key |
| [`PublisherView.swift`](AtunwaMediaExample/PublisherView.swift) | Publisher mode, with a publisher SDK key |
| [`Components.swift`](AtunwaMediaExample/Components.swift) | The SDK's mini player in SwiftUI, and the station row |
| [`Playback.swift`](AtunwaMediaExample/Playback.swift) | Observing the SDK's publishers |
| [`Info.plist`](AtunwaMediaExample/Info.plist) | Background audio, landscape for full-screen video, and the Ad Manager app ID |

## Run it

1. Copy `Config/Atunwa.local.xcconfig.example` to `Config/Atunwa.local.xcconfig` (git-ignored).
2. Set the mode and your key:

   ```
   ATUNWA_APP_MODE = single            // or publisher
   ATUNWA_PLAYER_KEY = …               // single
   ATUNWA_PUBLISHER_SDK_KEY = …        // publisher
   ```

3. Open `AtunwaMediaExample.xcodeproj`, pick your team under **Signing & Capabilities** if you run
   on a device, and run.

Xcode fetches the **MediaSDK** package from this repository the first time you open the project.
The app needs iOS 16 (the SDK itself supports iOS 15).

**Ad Manager app ID:** set `GAD_APPLICATION_IDENTIFIER` in `Atunwa.local.xcconfig` to your Ad
Manager app ID before you ship. Left empty, the SDK (1.0.6 and later) uses Google's sample app ID and
logs a warning.

For a walkthrough of how this app is built and what you could add to it, see
[its guide](../docs/ios.md).
