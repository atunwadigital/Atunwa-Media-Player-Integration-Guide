import MediaSDK
import SwiftUI

enum Tab: Hashable { case home, radio, tv, news, podcasts }

// MARK: Home: one screen that samples every section, the way large broadcasters open their apps.

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var playback: Playback
    @Binding var tab: Tab
    @Binding var podcastPath: [PodcastShow]
    @State private var article: URL?

    var body: some View {
        ScrollView {
            // A plain VStack, not a LazyVStack: the SDK's live card measures itself correctly in it.
            VStack(alignment: .leading, spacing: 0) {
                header

                // The SDK's live card: shows whatever is playing, or the featured channel until then.
                // Its play button plays in place; a tap on the card opens the full-screen player.
                SDKLiveCardViewRepresentable(previewPlayer: model.featuredStation) {
                    if playback.hasStream {
                        MediaSDK.shared.launchPlayer()
                    } else if let featured = model.featuredStation {
                        model.watchStation(featured)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)

                if !model.radioStations.isEmpty {
                    SectionHeader(title: "On air now", action: "All radio") { tab = .radio }
                    rail {
                        ForEach(model.radioStations, id: \.assetKey) { station in
                            let playing = playback.isPlaying(station: station)
                            StationCircle(station: station, isPlaying: playing) {
                                playing ? model.pause() : model.playStationInPlace(station)
                            }
                        }
                    }
                }

                if !model.tvStations.isEmpty {
                    SectionHeader(title: "Live TV", action: "All TV") { tab = .tv }
                    rail {
                        ForEach(model.tvStations, id: \.assetKey) { station in
                            TVTile(station: station, isPlaying: playback.isPlaying(station: station), width: 260) {
                                model.watchStation(station)
                            }
                        }
                    }
                }

                if !model.news.isEmpty {
                    SectionHeader(title: "Top stories", action: "More news") { tab = .news }
                    ForEach(model.news.prefix(3)) { story in
                        Button { article = story.url } label: { NewsRow(article: story) }.buttonStyle(.plain)
                    }
                }

                SectionHeader(title: "Catch up", action: "All shows") { tab = .tv }
                rail {
                    ForEach(model.catalogue.videos) { video in
                        VideoTile(video: video, isPlaying: playback.isPlaying(itemId: video.id), width: 240) {
                            model.watchVideo(video)
                        }
                    }
                }

                SectionHeader(title: "Podcasts", action: "All podcasts") { tab = .podcasts }
                rail {
                    ForEach(model.catalogue.podcasts) { show in
                        Button {
                            podcastPath = [show]
                            tab = .podcasts
                        } label: { PodcastTile(show: show) }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .refreshable { await model.refresh() }
        .sheet(item: $article) { SafariView(url: $0).ignoresSafeArea() }
    }

    private var header: some View {
        HStack(spacing: 12) {
            StationLogo(url: model.publisher?.publisher.logo, name: model.publisher?.publisher.name ?? "", size: 40)
            VStack(alignment: .leading) {
                Text(greeting).font(.footnote).foregroundStyle(Color.muted)
                Text(model.publisher?.publisher.name ?? "").font(.title2.bold())
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var greeting: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        default: return "Good evening"
        }
    }

    /// A horizontally scrolling rail of items, the shape of most publishers' home screens.
    private func rail<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 12) { content() }.padding(.horizontal, 20)
        }
    }
}

// MARK: Radio: every audio station, played in place. The mini player is the listener's remote.

struct RadioView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var playback: Playback

    var body: some View {
        ScrollView {
            ScreenTitle(title: "Radio", subtitle: "\(model.radioStations.count) stations, live")
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)], spacing: 12) {
                ForEach(model.radioStations, id: \.assetKey) { station in
                    let isActive = playback.liveStationKey == station.assetKey
                    let playing = playback.isPlaying(station: station)
                    let toggle = { playing ? model.pause() : model.playStationInPlace(station) }
                    Button(action: toggle) {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(alignment: .top) {
                                StationLogo(url: station.logo, name: station.playerName, size: 56)
                                Spacer()
                                PlayPauseButton(isPlaying: playing, size: 34, action: toggle)
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(station.playerName.trimmingCharacters(in: .whitespaces)).font(.headline).lineLimit(1)
                                HStack(spacing: 6) {
                                    if playing { Equalizer() }
                                    Text(isActive ? "On air" : "Radio").font(.caption).foregroundStyle(isActive ? Color.brand : Color.muted)
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(isActive ? Color.brand.opacity(0.18) : Color.surface, in: RoundedRectangle(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
    }
}

// MARK: TV: live channels open the SDK's full-screen player; catch-up videos are the publisher's
// own streams, handed to the same player as MediaItems.

struct TVView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var playback: Playback

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                ScreenTitle(title: "TV", subtitle: "Live channels and catch-up")
                ForEach(model.tvStations, id: \.assetKey) { station in
                    TVTile(station: station, isPlaying: playback.isPlaying(station: station)) { model.watchStation(station) }
                        .padding(.horizontal, 20)
                }
                HStack {
                    Text("Catch up").font(.title2.bold())
                    Spacer()
                    DemoLabel()
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                ForEach(model.catalogue.videos) { video in
                    Button { model.watchVideo(video) } label: {
                        HStack(spacing: 12) {
                            VideoTile(video: video, isPlaying: playback.isPlaying(itemId: video.id), width: 150, compact: true) { model.watchVideo(video) }
                            VStack(alignment: .leading, spacing: 2) {
                                Text(video.show.uppercased()).font(.caption2.bold()).foregroundStyle(Color.brand)
                                Text(video.title).font(.headline)
                                Text(video.summary).font(.footnote).foregroundStyle(Color.muted).lineLimit(2)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 20)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.bottom, 24)
        }
    }
}

// MARK: News: the publisher's feeds from the dashboard, fetched by the SDK, with an Ad Manager
// banner in the feed. Stories open in Safari inside the app, so whatever is playing keeps playing.

struct NewsView: View {
    @EnvironmentObject private var model: AppModel
    @State private var article: URL?

    var body: some View {
        Group {
            if model.news.isEmpty && model.isLoadingNews {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if model.news.isEmpty {
                MessageView(title: "No news yet", message: "Add news feeds to the publisher on the Atunwa dashboard.")
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ScreenTitle(title: "News", subtitle: model.publisher?.publisher.newsSources.map(\.name).joined(separator: ", ") ?? "")
                        if let lead = model.news.first {
                            Button { article = lead.url } label: { NewsHero(article: lead) }
                                .buttonStyle(.plain)
                                .padding(.horizontal, 20)
                        }
                        ForEach(Array(model.news.dropFirst().enumerated()), id: \.element.id) { index, story in
                            Button { article = story.url } label: { NewsRow(article: story) }.buttonStyle(.plain)
                            if index == 2, let unit = model.inFeedAdUnit {
                                InFeedBanner(adUnitId: unit)
                            }
                            Divider().padding(.horizontal, 20)
                        }
                    }
                    .padding(.bottom, 24)
                }
            }
        }
        .refreshable { await model.refresh() }
        .sheet(item: $article) { SafariView(url: $0).ignoresSafeArea() }
    }
}

// MARK: Podcasts: on-demand audio from the publisher's CMS (a bundled demo catalogue here),
// played in place through MediaSDK.load(item:).

struct PodcastsView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .top) {
                    ScreenTitle(title: "Podcasts", subtitle: "Shows from the newsroom")
                    Spacer()
                    DemoLabel().padding(.trailing, 20).padding(.top, 24)
                }
                ForEach(model.catalogue.podcasts) { show in
                    NavigationLink(value: show) {
                        HStack(spacing: 14) {
                            GradientArtwork(color: show.color, systemImage: "mic.fill").frame(width: 88, height: 88)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(show.title).font(.headline)
                                Text(show.host).font(.subheadline).foregroundStyle(Color.brand)
                                Text(show.summary).font(.footnote).foregroundStyle(Color.muted).lineLimit(2)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

struct PodcastDetailView: View {
    let show: PodcastShow
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var playback: Playback

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                GradientArtwork(color: show.color, title: show.title, systemImage: "mic.fill").frame(width: 180, height: 180)
                Text(show.title).font(.title.bold()).padding(.top, 8)
                Text(show.host).foregroundStyle(Color.brand)
                Text(show.summary).foregroundStyle(Color.muted)
                DemoLabel()
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, alignment: .leading)

            LazyVStack(spacing: 0) {
                ForEach(show.episodes) { episode in
                    let isActive = playback.itemId == episode.id
                    let playing = playback.isPlaying(itemId: episode.id)
                    let toggle = { playing ? model.pause() : model.playEpisode(episode, of: show) }
                    Button(action: toggle) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(episode.published).font(.caption).foregroundStyle(Color.muted)
                                Text(episode.title).font(.body.weight(.semibold)).foregroundStyle(isActive ? Color.brand : .primary)
                                    .multilineTextAlignment(.leading)
                                Text(formatDuration(episode.durationSeconds)).font(.caption).foregroundStyle(Color.muted)
                            }
                            Spacer()
                            PlayPauseButton(isPlaying: playing, action: toggle)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Divider().padding(.horizontal, 20)
                }
            }
            .padding(.top, 16)
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ScreenTitle: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.largeTitle.bold())
            if !subtitle.isEmpty { Text(subtitle).foregroundStyle(Color.muted) }
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
