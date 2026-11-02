package com.atunwadigital.examples.mediahouse.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atunwadigital.examples.mediahouse.BuildConfig
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.NewsArticle
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.models.PublisherConfig
import com.atunwadigital.mediasdk.models.SDKThemeConfig
import com.atunwadigital.mediasdk.models.StreamType
import kotlinx.coroutines.launch

/** The brand color the SDK's player screens use. */
const val BRAND_COLOR_HEX = "#FF4F3F"

/**
 * The app's content and every SDK call it makes. One publisher SDK key gives the whole app its
 * live content: radio and TV stations, their branding and ads, and the publisher's news feeds.
 */
class MediaHouseViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val sdkKey = BuildConfig.ATUNWA_PUBLISHER_SDK_KEY
    private val theme = SDKThemeConfig(primaryColorHex = BRAND_COLOR_HEX)

    val hasKey = sdkKey.isNotBlank()
    val catalogue: Catalogue = Catalogue.load(application)

    var publisher by mutableStateOf<PublisherConfig?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var news by mutableStateOf<List<NewsArticle>>(emptyList())
        private set
    var isLoadingNews by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    val radioStations: List<PlayerConfig> get() = publisher?.players.orEmpty().filter { it.streamChannels?.isVideo != true }
    val tvStations: List<PlayerConfig> get() = publisher?.players.orEmpty().filter { it.streamChannels?.isVideo == true }

    /** The station the home screen features: the publisher's first one, as ordered on the dashboard. */
    val featuredStation: PlayerConfig? get() = publisher?.players?.firstOrNull()

    /** A Google Ad Manager ad unit for in-feed banners. This reuses a station's from the dashboard. */
    val inFeedAdUnit: String? get() = publisher?.players?.firstNotNullOfOrNull { it.gptUrl.ifBlank { null } }

    init {
        if (hasKey) load()
    }

    fun load() = viewModelScope.launch {
        error = null
        MediaSDK.setup(context)
        // Uses the SDK's memory or disk cache when it has one, so the app also starts offline.
        MediaSDK.initializePublisher(context, sdkKey, theme)
            .onSuccess { publisher = it; loadNews() }
            .onFailure { error = it.message ?: "Check the publisher SDK key and your connection." }
    }

    /** Pull to refresh: always goes to the network, for the stations and the news. */
    fun refresh() = viewModelScope.launch {
        isRefreshing = true
        MediaSDK.refreshPublisherConfig(context, sdkKey).onSuccess { publisher = it }
        loadNews()
        isRefreshing = false
    }

    /** Fetches every news feed configured for the publisher, merged and newest first. */
    private suspend fun loadNews() {
        val sources = publisher?.publisher?.newsSources.orEmpty()
        if (sources.isEmpty()) return
        isLoadingNews = true
        news = MediaSDK.fetchNews(sources)
        isLoadingNews = false
    }

    // Playback. Each kind of content uses a different SDK call.

    /** Radio: switch station and play in place. The mini player follows the listener around the app. */
    fun playStationInPlace(station: PlayerConfig) = withSdk {
        if (isStationLoaded(station)) {
            MediaSDK.play()
        } else if (MediaSDK.playerConfig?.assetKey == station.assetKey) {
            // Still the active station, but something on demand replaced its stream: reload it.
            MediaSDK.load()
        } else {
            MediaSDK.activatePlayer(context, station)
        }
    }

    /** Live TV: switch station and open the SDK's full-screen player (landscape video, PiP, Cast). */
    fun watchStation(activity: Context, station: PlayerConfig) = withSdk {
        if (MediaSDK.playerConfig?.assetKey == station.assetKey && !isStationLoaded(station)) {
            MediaSDK.load()
            MediaSDK.launchPlayer(activity)
        } else {
            MediaSDK.loadPlayer(activity, station)
        }
    }

    /** Catch-up video: hand the SDK any stream as a MediaItem, then open its full-screen player. */
    fun watchVideo(activity: Context, video: Video) = withSdk {
        MediaSDK.load(video.toMediaItem(), autoplay = true)
        MediaSDK.launchPlayer(activity)
    }

    /** Podcast episode: on-demand audio played in place, with the mini player as its controls. */
    fun playEpisode(show: PodcastShow, episode: Episode) = withSdk {
        if (MediaSDK.currentMediaItem?.id == episode.id) MediaSDK.play()
        else MediaSDK.load(episode.toMediaItem(show), autoplay = true)
    }

    fun pause() = MediaSDK.pause()

    /**
     * The mini player's close button. reset() stops playback and clears the SDK's state, including
     * the publisher config; the next play initializes again (from the cache, so it's instant).
     */
    fun stop() = MediaSDK.reset()

    private fun isStationLoaded(station: PlayerConfig): Boolean =
        MediaSDK.playerConfig?.assetKey == station.assetKey &&
            MediaSDK.currentMediaItem?.streamType == StreamType.LIVE

    /** Runs [action] once the SDK is set up and the publisher is loaded, as it is after reset(). */
    private fun withSdk(action: () -> Unit) = viewModelScope.launch {
        if (!MediaSDK.isInitialized) MediaSDK.setup(context)
        if (MediaSDK.publisherConfig == null) MediaSDK.initializePublisher(context, sdkKey, theme)
        action()
    }
}
