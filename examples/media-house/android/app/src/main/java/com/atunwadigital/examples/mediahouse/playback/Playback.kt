package com.atunwadigital.examples.mediahouse.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.SDKState
import com.atunwadigital.mediasdk.models.StreamType

/** A snapshot of the SDK's playback, for deciding what the screens highlight. */
data class Playback(
    val state: SDKState,
    val playWhenReady: Boolean,
    /** The id of the loaded MediaItem: a catch-up video or podcast episode id, for on-demand items. */
    val itemId: String?,
    /** The asset key of the station whose live stream is loaded, if one is. */
    val liveStationKey: String?,
    /** The SDK's full-screen player is open, including while it's in Picture-in-Picture. */
    val isPlayerOpen: Boolean,
) {
    val hasStream: Boolean get() = state != SDKState.IDLE && state != SDKState.INITIALIZING

    val isPlaying: Boolean
        get() = playWhenReady && state in setOf(SDKState.LOADING, SDKState.BUFFERING, SDKState.PLAYING, SDKState.AD_PLAYING)

    fun isPlayingStation(assetKey: String) = isPlaying && liveStationKey == assetKey
    fun isPlayingItem(id: String) = isPlaying && itemId == id
}

@Composable
fun rememberPlayback(): Playback {
    val state by MediaSDK.state.collectAsStateWithLifecycle()
    val playWhenReady by MediaSDK.playWhenReady.collectAsStateWithLifecycle()
    val isPlayerOpen by MediaSDK.isDedicatedPlayerActive.collectAsStateWithLifecycle()
    val item by MediaSDK.currentMediaItemFlow.collectAsStateWithLifecycle()
    return Playback(
        state = state,
        playWhenReady = playWhenReady,
        itemId = item?.id,
        liveStationKey = if (item?.streamType == StreamType.LIVE) MediaSDK.playerConfig?.assetKey else null,
        isPlayerOpen = isPlayerOpen,
    )
}
