package com.atunwadigital.examples.media.single

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.atunwadigital.examples.media.ui.kindLabel
import com.atunwadigital.examples.media.ui.rememberPlayback
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import kotlinx.coroutines.launch

/**
 * Single-player mode: an app for **one station**, identified by its player API key.
 *
 * 1. [MediaSDK.initialize] fetches the station's config and makes it the active station, so its
 *    name, logo and colors are available before anything plays.
 * 2. [MediaSDK.load] starts its stream, and [MediaSDK.launchPlayer] opens the SDK's full-screen
 *    player.
 *
 * If you don't need the station's details before playback starts, one call does both steps:
 * `MediaSDK.launchPlayer(context, playerApiKey)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinglePlayerScreen(playerApiKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = rememberPlayback()

    var station by remember { mutableStateOf(MediaSDK.playerConfig?.takeIf { it.assetKey == playerApiKey }) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun initialize() {
        error = null
        MediaSDK.initialize(context.applicationContext, playerApiKey)
            .onSuccess { station = MediaSDK.playerConfig }
            .onFailure { error = it.message ?: "Check the player API key and your connection." }
    }

    // Already active after a configuration change: initializing again would interrupt playback.
    LaunchedEffect(playerApiKey) { if (station == null) initialize() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(station?.playerName ?: "Atunwa") }) },
        bottomBar = {
            MiniPlayer(
                playback = playback,
                onClose = {
                    // reset() stops playback and clears the active station. Initialize again
                    // (from the SDK's cache, so it's instant) to be ready for the next play.
                    MediaSDK.reset()
                    scope.launch { initialize() }
                },
            )
        },
    ) { padding ->
        val current = station
        when {
            current == null && error != null -> MessageState(
                title = "Couldn't load the station",
                message = error.orEmpty(),
                actionLabel = "Try again",
                onAction = { scope.launch { initialize() } },
                modifier = Modifier.padding(padding),
            )
            current == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (playback.hasStream) {
                    // Once the stream is loaded, the SDK's live card plays and pauses in place.
                    LiveCard(previewPlayer = current, onClick = { MediaSDK.launchPlayer(context) })
                } else {
                    StationHero(
                        station = current,
                        onListen = { startStation(context, openPlayer = true) },
                        onPlayHere = { startStation(context, openPlayer = false) },
                    )
                }
                Text(
                    "This build uses a player API key, so it plays this one station. To offer all " +
                        "of a publisher's stations, build with a publisher SDK key instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Starts the active station's stream if it isn't loaded yet, and optionally opens the full-screen
 * player. The player plays any preroll the station has before the stream.
 */
private fun startStation(context: Context, openPlayer: Boolean) {
    if (MediaSDK.currentMediaItem == null) MediaSDK.load()
    if (openPlayer) MediaSDK.launchPlayer(context)
}

/** The station's branding and play buttons, shown before its stream is loaded. */
@Composable
private fun StationHero(station: PlayerConfig, onListen: () -> Unit, onPlayHere: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StationLogo(station.logo, 96.dp)
            Spacer(Modifier.height(16.dp))
            Text(station.playerName, style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(station.kindLabel, station.publisher?.name).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row {
                Button(onClick = onListen) { Text(if (station.streamChannels?.isVideo == true) "Watch live" else "Listen live") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onPlayHere) { Text("Play here") }
            }
        }
    }
}
