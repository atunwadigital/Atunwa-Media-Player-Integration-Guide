import Foundation
import MediaSDK

// On-demand content. The SDK serves live stations and news from the Atunwa dashboard; videos and
// podcasts come from the publisher's own CMS. This example reads a bundled demo catalogue
// (catalogue.json) instead, and hands each item to the SDK as a MediaItem.

struct Video: Decodable, Identifiable {
    let id: String
    let title: String
    let show: String
    let summary: String
    let durationSeconds: Int
    let color: String
    let url: String

    /// An on-demand video for the SDK. The full-screen player shows it in its TV layout.
    var mediaItem: MediaItem {
        MediaItem(
            id: id,
            streamUrl: URL(string: url)!,
            streamChannels: StreamChannels(isAudio: true, isVideo: true),
            mediaType: .video,
            streamType: .vod,
            metadata: MediaMetadata(title: title, artistOrShow: show)
        )
    }
}

struct Episode: Decodable, Identifiable {
    let id: String
    let title: String
    let published: String
    let durationSeconds: Int
    let url: String

    /// A podcast episode for the SDK: on-demand audio, with the show as the subtitle.
    func mediaItem(show: PodcastShow) -> MediaItem {
        MediaItem(
            id: id,
            streamUrl: URL(string: url)!,
            streamChannels: StreamChannels(isAudio: true, isVideo: false),
            mediaType: .audio,
            streamType: .vod,
            metadata: MediaMetadata(title: title, artistOrShow: show.title)
        )
    }
}

struct PodcastShow: Decodable, Identifiable, Hashable {
    let id: String
    let title: String
    let host: String
    let summary: String
    let color: String
    let episodes: [Episode]

    static func == (a: PodcastShow, b: PodcastShow) -> Bool { a.id == b.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

struct Catalogue: Decodable {
    let videos: [Video]
    let podcasts: [PodcastShow]

    static func load() -> Catalogue {
        guard let url = Bundle.main.url(forResource: "catalogue", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let catalogue = try? JSONDecoder().decode(Catalogue.self, from: data)
        else { return Catalogue(videos: [], podcasts: []) }
        return catalogue
    }
}

/// "9:56" or "1:02:03".
func formatDuration(_ seconds: Int) -> String {
    let h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60
    return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
}
