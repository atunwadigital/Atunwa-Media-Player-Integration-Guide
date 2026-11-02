import MediaSDK
import SwiftUI

/// A radio station as a round logo, the way most radio apps present "on air now".
struct StationCircle: View {
    let station: PlayerConfig
    let isPlaying: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack(alignment: .bottomTrailing) {
                    StationLogo(url: station.logo, name: station.playerName, size: 76, circle: true)
                        .overlay(Circle().stroke(Color.brand, lineWidth: isPlaying ? 3 : 0))
                    if isPlaying {
                        Equalizer().padding(5).background(.black, in: Circle())
                    }
                }
                Text(station.playerName.trimmingCharacters(in: .whitespaces))
                    .font(.caption.weight(isPlaying ? .bold : .regular))
                    .lineLimit(1)
            }
            .frame(width: 84)
        }
        .buttonStyle(.plain)
    }
}

/// A 16:9 tile with content laid over the bottom, for TV channels and videos.
struct WideTile<Background: View, Overlay: View>: View {
    var width: CGFloat?
    let action: () -> Void
    @ViewBuilder let background: () -> Background
    @ViewBuilder let overlay: () -> Overlay

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomLeading) {
                background()
                LinearGradient(colors: [.clear, .black.opacity(0.75)], startPoint: .center, endPoint: .bottom)
                HStack { overlay() }.padding(10)
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            .frame(width: width)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// A live TV channel as a 16:9 tile.
struct TVTile: View {
    let station: PlayerConfig
    let isPlaying: Bool
    var width: CGFloat?
    let action: () -> Void

    var body: some View {
        WideTile(width: width, action: action) {
            ZStack {
                LinearGradient(colors: [Color(white: 0.17), Color(white: 0.07)], startPoint: .topLeading, endPoint: .bottomTrailing)
                StationLogo(url: station.logo, name: station.playerName, size: 64)
            }
        } overlay: {
            LiveBadge()
            Text(station.playerName.trimmingCharacters(in: .whitespaces)).font(.headline).lineLimit(1)
            Spacer(minLength: 0)
            if isPlaying { Equalizer(color: .white) }
        }
    }
}

/// A catch-up video as a 16:9 tile. `compact` shows only the duration, for rows that print the
/// title beside the tile.
struct VideoTile: View {
    let video: Video
    let isPlaying: Bool
    var width: CGFloat?
    var compact = false
    let action: () -> Void

    var body: some View {
        WideTile(width: width, action: action) {
            GradientArtwork(color: video.color, systemImage: "play.circle.fill")
        } overlay: {
            if compact {
                Text(formatDuration(video.durationSeconds)).font(.caption.bold())
            } else {
                VStack(alignment: .leading, spacing: 2) {
                    Text(video.title).font(.headline).lineLimit(1)
                    Text("\(video.show) · \(formatDuration(video.durationSeconds))").font(.caption).foregroundStyle(.white.opacity(0.8))
                }
            }
            Spacer(minLength: 0)
            if isPlaying { Equalizer(color: .white) }
        }
    }
}

/// A podcast as square artwork.
struct PodcastTile: View {
    let show: PodcastShow
    var size: CGFloat = 140

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            GradientArtwork(color: show.color, title: show.title, systemImage: "mic.fill")
                .frame(width: size, height: size)
            Text(show.host).font(.caption).foregroundStyle(Color.muted).lineLimit(1)
        }
        .frame(width: size)
    }
}

/// The lead story: a large image with the headline over it.
struct NewsHero: View {
    let article: NewsArticle

    var body: some View {
        // The frame comes first and the image is laid over it, so a large image can't resize it.
        Color.surface
            .aspectRatio(16 / 10, contentMode: .fit)
            .overlay {
                AsyncImage(url: article.thumbnailUrl.flatMap(URL.init(string:))) { $0.resizable().scaledToFill() } placeholder: { Color.surface }
            }
            .overlay(alignment: .bottomLeading) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .center, endPoint: .bottom)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(article.sourceName.uppercased()).font(.caption2.bold()).foregroundStyle(Color.brand)
                        Text(article.title).font(.title3.bold()).lineLimit(3)
                    }
                    .padding(16)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// A story in a list: headline, source and time, with a thumbnail.
struct NewsRow: View {
    let article: NewsArticle

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(article.title).font(.subheadline.weight(.semibold)).lineLimit(3).multilineTextAlignment(.leading)
                Text("\(article.sourceName) · \(article.publishedAt.formatted(.relative(presentation: .named)))")
                    .font(.caption).foregroundStyle(Color.muted)
            }
            Spacer(minLength: 0)
            AsyncImage(url: article.thumbnailUrl.flatMap(URL.init(string:))) { $0.resizable().scaledToFill() } placeholder: { Color.surface }
                .frame(width: 96, height: 72)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}
