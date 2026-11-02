import MediaSDK
import SwiftUI

/// Single-player mode: an app for **one station**, identified by its player API key.
///
/// 1. `initialize(apiKey:)` fetches the station's config and makes it the active station, so its
///    name, logo and colors are available before anything plays.
/// 2. `load()` starts its stream, and `launchPlayer()` presents the SDK's full-screen player.
///
/// If you don't need the station's details before playback starts, one call does both steps:
/// `MediaSDK.shared.launchPlayer(apiKey: playerApiKey)`.
struct SinglePlayerView: View {
    let playerApiKey: String

    @EnvironmentObject private var playback: Playback
    @State private var station: PlayerConfig?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(station?.playerName ?? "Atunwa")
                .navigationBarTitleDisplayMode(.inline)
        }
        .miniPlayer(playback) {
            // reset() stops playback and clears the active station. Initialize again (from the
            // SDK's cache, so it's instant) to be ready for the next play.
            MediaSDK.shared.reset()
            Task { await initialize() }
        }
        .task {
            // Already active (the view was recreated): initializing again would interrupt playback.
            if let active = MediaSDK.shared.playerConfig, active.assetKey == playerApiKey {
                station = active
            } else {
                await initialize()
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let station {
            ScrollView {
                VStack(spacing: 16) {
                    if playback.hasStream {
                        // Once the stream is loaded, the SDK's live card plays and pauses in place.
                        SDKLiveCardViewRepresentable(previewPlayer: station) {
                            MediaSDK.shared.launchPlayer()
                        }
                    } else {
                        StationHero(
                            station: station,
                            onListen: { start(openPlayer: true) },
                            onPlayHere: { start(openPlayer: false) }
                        )
                    }
                    Text("This build uses a player API key, so it plays this one station. To offer all of a publisher's stations, build with a publisher SDK key instead.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding()
            }
        } else if let error {
            MessageView(title: "Couldn't load the station", message: error, actionTitle: "Try again") {
                Task { await initialize() }
            }
        } else {
            ProgressView()
        }
    }

    private func initialize() async {
        error = nil
        do {
            try await MediaSDK.shared.initialize(apiKey: playerApiKey)
            station = MediaSDK.shared.playerConfig
        } catch {
            self.error = "Check the player API key and your connection. (\(error.localizedDescription))"
        }
    }

    /// Starts the active station's stream if it isn't loaded yet, and optionally presents the
    /// full-screen player. The player plays any preroll the station has before the stream.
    private func start(openPlayer: Bool) {
        let sdk = MediaSDK.shared
        if sdk.currentMediaItem == nil {
            try? sdk.load()   // throws only when no station is active
        }
        if openPlayer {
            sdk.launchPlayer()
        }
    }
}

/// The station's branding and play buttons, shown before its stream is loaded.
private struct StationHero: View {
    let station: PlayerConfig
    let onListen: () -> Void
    let onPlayHere: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            StationLogo(url: station.logo, size: 96)
                .padding(.bottom, 8)
            Text(station.playerName).font(.title2.bold())
            Text([station.kindLabel, station.publisher?.name].compactMap { $0 }.joined(separator: " · "))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Button(station.isVideo ? "Watch live" : "Listen live", action: onListen)
                    .buttonStyle(.borderedProminent)
                Button("Play here", action: onPlayHere)
                    .buttonStyle(.bordered)
            }
            .padding(.top, 12)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}
