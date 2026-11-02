import MediaSDK
import SwiftUI

/// The SDK's mini player bar, for SwiftUI. A tap or an upward swipe reopens the full-screen player.
/// It observes the SDK on its own; `onClose` is its close button, which should call `reset()`.
struct MiniPlayerBar: UIViewRepresentable {
    var onClose: () -> Void

    func makeUIView(context: Context) -> SDKMiniPlayerView {
        let bar = SDKMiniPlayerView()
        bar.onExpandListener = { MediaSDK.shared.launchPlayer() }
        return bar
    }

    func updateUIView(_ bar: SDKMiniPlayerView, context: Context) {
        bar.onCloseClickListener = onClose
    }
}

extension View {
    /// Pins the SDK's mini player to the bottom of the screen while a stream is loaded and the
    /// full-screen player isn't open.
    func miniPlayer(_ playback: Playback, onClose: @escaping () -> Void) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            if playback.showsMiniPlayer {
                MiniPlayerBar(onClose: onClose)
                    .frame(height: 62)
                    .transition(.move(edge: .bottom))
            }
        }
        .animation(.default, value: playback.showsMiniPlayer)
    }
}

extension PlayerConfig {
    var isVideo: Bool { streamChannels?.isVideo == true }
    /// "TV" for video stations, "Radio" otherwise.
    var kindLabel: String { isVideo ? "TV" : "Radio" }
}

struct StationLogo: View {
    let url: String?
    let size: CGFloat

    var body: some View {
        AsyncImage(url: url.flatMap(URL.init(string:))) { image in
            image.resizable().scaledToFit()
        } placeholder: {
            Color(.secondarySystemFill)
        }
        .frame(width: size, height: size)
        .background(Color(.secondarySystemFill))
        .clipShape(RoundedRectangle(cornerRadius: size / 5))
    }
}

/// A station in a list. Tapping the row opens the full-screen player; the trailing button plays or
/// pauses in place.
struct StationRow: View {
    let station: PlayerConfig
    let isActive: Bool
    let isPlaying: Bool
    let onOpen: () -> Void
    let onPlayPause: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 12) {
                    StationLogo(url: station.logo, size: 48)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(station.playerName)
                            .font(.headline.weight(isActive ? .bold : .medium))
                            .lineLimit(1)
                        Text(isActive ? "\(station.kindLabel) · Now playing" : station.kindLabel)
                            .font(.subheadline)
                            .foregroundStyle(isActive ? Color.accentColor : .secondary)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Button(action: onPlayPause) {
                Image(systemName: isActive && isPlaying ? "pause.circle.fill" : "play.circle.fill")
                    .font(.title)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isActive && isPlaying ? "Pause" : "Play")
        }
        .listRowBackground(isActive ? Color.accentColor.opacity(0.08) : nil)
    }
}

/// A centered message with an optional action, for errors and empty lists.
struct MessageView: View {
    let title: String
    let message: String
    var actionTitle: String?
    var action: () -> Void = {}

    var body: some View {
        VStack(spacing: 12) {
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary)
            if let actionTitle {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
        .multilineTextAlignment(.center)
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Shown when the build has no Atunwa key.
struct MissingKeyView: View {
    let problem: String

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Add your Atunwa key").font(.title2.bold())
            Text(problem)
            Text("Copy Config/Atunwa.local.xcconfig.example to Config/Atunwa.local.xcconfig, set the mode and the key Atunwa gave you, then build again:")
                .font(.subheadline)
            Text("ATUNWA_APP_MODE = single\nATUNWA_PLAYER_KEY = …\n\n// or\nATUNWA_APP_MODE = publisher\nATUNWA_PUBLISHER_SDK_KEY = …")
                .font(.system(.footnote, design: .monospaced))
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 8))
        }
        .padding(24)
        .frame(maxHeight: .infinity)
    }
}
