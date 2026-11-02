import MediaSDK
import SafariServices
import SwiftUI

extension Color {
    static let brand = Color(hex: brandColorHex)
    static let surface = Color(white: 0.11)
    static let muted = Color(white: 0.62)

    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&value)
        self.init(red: Double((value >> 16) & 0xFF) / 255, green: Double((value >> 8) & 0xFF) / 255, blue: Double(value & 0xFF) / 255)
    }
}

// MARK: SDK views

/// The SDK's mini player bar. A tap or an upward swipe reopens the full-screen player.
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
    /// One mini player for the whole app, above the tab bar: whatever is playing (a station, a
    /// video or an episode) stays one tap away from every screen.
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

/// A Google Ad Manager banner in a feed, through the SDK's standalone banner view. It takes no
/// space until an ad fills.
struct InFeedBanner: View {
    let adUnitId: String
    @State private var hasAd = false
    @State private var isReady = false

    var body: some View {
        Group {
            if isReady {
                SDKGptBannerViewRepresentable(adUnitId: adUnitId) { hasAd = $0 }
                    .frame(width: 300, height: hasAd ? 250 : 1)
                    .frame(maxWidth: .infinity)
                    .opacity(hasAd ? 1 : 0)
            }
        }
        // Google Mobile Ads takes a moment to start after launch; create the banner once it has.
        .task {
            while !MediaSDK.shared.gptManager.isAdsEnabled {
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
            isReady = true
        }
    }
}

// MARK: Building blocks

struct SectionHeader: View {
    let title: String
    var action: String?
    var onAction: () -> Void = {}

    var body: some View {
        HStack {
            Text(title).font(.title2.bold())
            Spacer()
            if let action {
                Button(action, action: onAction).foregroundStyle(Color.brand)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 8)
    }
}

/// A station logo on a light chip, since most logos are designed for white backgrounds. Stations
/// without a logo on the dashboard get their initials instead.
struct StationLogo: View {
    let url: String?
    let name: String
    let size: CGFloat
    var circle = false

    var body: some View {
        ZStack {
            Color.white
            if let url, !url.isEmpty, let link = URL(string: url) {
                AsyncImage(url: link) { $0.resizable().scaledToFit() } placeholder: { Color.white }
                    .padding(size / 10)
            } else {
                Text(initials).font(.system(size: size / 3, weight: .black)).foregroundStyle(.black)
            }
        }
        .frame(width: size, height: size)
        .clipShape(circle ? AnyShape(Circle()) : AnyShape(RoundedRectangle(cornerRadius: size / 5)))
    }

    private var initials: String {
        name.split(separator: " ").prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

/// Artwork for content with no image of its own: a gradient in its color.
struct GradientArtwork: View {
    let color: String
    var title = ""
    var systemImage: String?

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [Color(hex: color), Color(hex: color).opacity(0.55), .black], startPoint: .topLeading, endPoint: .bottomTrailing)
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: 34)).foregroundStyle(.white.opacity(0.9))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            if !title.isEmpty {
                Text(title).font(.headline).foregroundStyle(.white).lineLimit(2).padding(10)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct LiveBadge: View {
    var body: some View {
        HStack(spacing: 4) {
            Circle().fill(.white).frame(width: 6, height: 6)
            Text("LIVE").font(.caption2.bold())
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(Color.brand, in: Capsule())
    }
}

/// Three bouncing bars, shown on whatever is playing.
struct Equalizer: View {
    var color: Color = .brand
    @State private var animating = false

    var body: some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<3) { index in
                RoundedRectangle(cornerRadius: 1)
                    .fill(color)
                    .frame(width: 3, height: animating ? 14 : 4)
                    .animation(.easeInOut(duration: [0.38, 0.52, 0.3][index]).repeatForever(), value: animating)
            }
        }
        .frame(height: 14, alignment: .bottom)
        .onAppear { animating = true }
    }
}

struct PlayPauseButton: View {
    let isPlaying: Bool
    var size: CGFloat = 40
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                .font(.system(size: size * 0.4, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Color.brand, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isPlaying ? "Pause" : "Play")
    }
}

/// A small label for the bundled on-demand content.
struct DemoLabel: View {
    var body: some View {
        Text("DEMO CONTENT")
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(Color.muted)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.muted.opacity(0.5)))
    }
}

struct MessageView: View {
    let title: String
    let message: String
    var actionTitle: String?
    var action: () -> Void = {}

    var body: some View {
        VStack(spacing: 12) {
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(Color.muted)
            if let actionTitle {
                Button(actionTitle, action: action).buttonStyle(.borderedProminent)
            }
        }
        .multilineTextAlignment(.center)
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Opens a story in Safari inside the app, so whatever is playing keeps playing.
struct SafariView: UIViewControllerRepresentable {
    let url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

extension NewsArticle {
    var url: URL? { URL(string: link) }
}

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}
