package com.atunwadigital.examples.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.SDKState

/** A snapshot of the SDK's playback, for deciding what the screens show. */
data class Playback(
    val state: SDKState,
    val playWhenReady: Boolean,
    /** The asset key of the active station, if any. */
    val activeAssetKey: String?,
    /** The SDK's full-screen player is open, including while it's in Picture-in-Picture. */
    val isPlayerOpen: Boolean,
) {
    /** A stream is loaded: playing, paused, buffering, in an ad, or failed. */
    val hasStream: Boolean get() = state != SDKState.IDLE && state != SDKState.INITIALIZING

    val isPlaying: Boolean
        get() = playWhenReady && state in setOf(SDKState.LOADING, SDKState.BUFFERING, SDKState.PLAYING, SDKState.AD_PLAYING)
}

/** Observes the SDK's state flows while the screen is started. */
@Composable
fun rememberPlayback(): Playback {
    val state by MediaSDK.state.collectAsStateWithLifecycle()
    val playWhenReady by MediaSDK.playWhenReady.collectAsStateWithLifecycle()
    val isPlayerOpen by MediaSDK.isDedicatedPlayerActive.collectAsStateWithLifecycle()
    // Collected so a station switch recomposes, then read the active station from playerConfig.
    val mediaItem by MediaSDK.currentMediaItemFlow.collectAsStateWithLifecycle()
    return Playback(
        state = state,
        playWhenReady = playWhenReady,
        activeAssetKey = mediaItem.let { MediaSDK.playerConfig?.assetKey },
        isPlayerOpen = isPlayerOpen,
    )
}
