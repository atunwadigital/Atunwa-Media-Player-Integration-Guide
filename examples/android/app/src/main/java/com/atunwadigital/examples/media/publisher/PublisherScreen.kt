package com.atunwadigital.examples.media.publisher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.atunwadigital.examples.media.ui.LiveCard
import com.atunwadigital.examples.media.ui.LoadingState
import com.atunwadigital.examples.media.ui.MessageState
import com.atunwadigital.examples.media.ui.MiniPlayer
import com.atunwadigital.examples.media.ui.StationLogo
import com.atunwadigital.examples.media.ui.StationRow
import com.atunwadigital.examples.media.ui.rememberPlayback
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PublisherConfig
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { All("All"), Radio("Radio"), Tv("TV") }

/**
 * Publisher mode: an app offering **every station** a publisher has, identified by the
 * publisher's SDK key.
 *
 * 1. [MediaSDK.initializePublisher] fetches the publisher and its stations. It doesn't start
 *    anything.
 * 2. [MediaSDK.loadPlayer] switches to a station and opens the SDK's full-screen player.
 *    [MediaSDK.activatePlayer] switches to it and plays in place.
 *
 * The full-screen player also shows a rail of the publisher's other TV stations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublisherScreen(publisherSdkKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = rememberPlayback()
    val snackbar = remember { SnackbarHostState() }

    var config by remember { mutableStateOf(MediaSDK.publisherConfig) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }

    // Uses the SDK's memory or disk cache when it has one, so the app also starts offline.
    suspend fun initialize() {
        error = null
        MediaSDK.initializePublisher(context.applicationContext, publisherSdkKey)
            .onSuccess { config = it }
            .onFailure { error = it.message ?: "Check the publisher SDK key and your connection." }
    }

    // Always goes to the network, so a failure is reported rather than hidden by the cache.
    suspend fun refresh() {
        isRefreshing = true
        MediaSDK.refreshPublisherConfig(context.applicationContext, publisherSdkKey)
            .onSuccess { config = it }
            .onFailure { snackbar.showSnackbar("Couldn't refresh the stations.") }
        isRefreshing = false
    }

    LaunchedEffect(publisherSdkKey) { if (config == null) initialize() }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    config?.publisher?.logo?.let { StationLogo(it, 32.dp) }
                    Spacer(Modifier.width(12.dp))
                    Text(config?.publisher?.name ?: "Atunwa")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            MiniPlayer(
                playback = playback,
                onClose = {
                    // reset() also clears publisherConfig. Initialize again (from the SDK's
                    // cache, so it's instant) so the player's related-channels rail keeps working.
                    MediaSDK.reset()
                    scope.launch { initialize() }
                },
            )
        },
    ) { padding ->
        val current = config
        when {
            current == null && error != null -> MessageState(
                title = "Couldn't load the stations",
                message = error.orEmpty(),
                actionLabel = "Try again",
                onAction = { scope.launch { initialize() } },
                modifier = Modifier.padding(padding),
            )
            current == null -> LoadingState(Modifier.padding(padding))
            current.players.isEmpty() -> MessageState(
                title = "No stations yet",
                message = "This publisher has no active stations on the Atunwa dashboard.",
                modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { scope.launch { refresh() } },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                StationList(
                    config = current,
                    filter = filter,
                    onFilterChange = { filter = it },
                    activeAssetKey = playback.activeAssetKey,
                    isPlaying = playback.isPlaying,
                )
            }
        }
    }
}

@Composable
private fun StationList(
    config: PublisherConfig,
    filter: Filter,
    onFilterChange: (Filter) -> Unit,
    activeAssetKey: String?,
    isPlaying: Boolean,
) {
    val context = LocalContext.current
    val stations = config.players.filter {
        when (filter) {
            Filter.All -> true
            Filter.Radio -> it.streamChannels?.isVideo != true
            Filter.Tv -> it.streamChannels?.isVideo == true
        }
    }
    val hasTv = config.players.any { it.streamChannels?.isVideo == true }
    val hasRadio = config.players.any { it.streamChannels?.isVideo != true }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // Shows the active station, or the publisher's first station until one is picked.
            // Its own play button starts that station in place.
            val defaultStation = config.players.first()
            LiveCard(
                previewPlayer = defaultStation,
                onClick = {
                    if (MediaSDK.playerConfig != null) MediaSDK.launchPlayer(context)
                    else MediaSDK.loadPlayer(context, defaultStation)
                },
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${config.players.size} stations",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (hasTv && hasRadio) {
                    Filter.entries.forEach {
                        FilterChip(selected = filter == it, onClick = { onFilterChange(it) }, label = { Text(it.label) })
                    }
                }
            }
        }
        items(stations, key = { it.assetKey }) { station ->
            val isActive = station.assetKey == activeAssetKey
            StationRow(
                station = station,
                isActive = isActive,
                isPlaying = isPlaying,
                onOpen = { MediaSDK.loadPlayer(context, station) },
                onPlayPause = {
                    when {
                        !isActive -> MediaSDK.activatePlayer(context, station)
                        isPlaying -> MediaSDK.pause()
                        else -> MediaSDK.play()
                    }
                },
            )
        }
    }
}
