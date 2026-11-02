package com.atunwadigital.examples.media.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView

// The SDK's views are Android Views. These wrap them for Compose. They observe the SDK on their
// own, so nothing here pushes playback state into them.

/**
 * The SDK's "now playing" card: station branding, a waveform or live video, ads, and a play/pause
 * button that plays in place. Its height always follows its width at 16:9.
 *
 * @param previewPlayer the station to show before anything is active. Its play button starts it.
 * @param onClick a tap on the card body (not its play button).
 */
@Composable
fun LiveCard(previewPlayer: PlayerConfig?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context -> SDKLiveCardView(context) },
        update = { card ->
            card.previewPlayer = previewPlayer
            card.onCardClickListener = onClick
        },
    )
}

/**
 * The SDK's mini player bar, shown while a stream is loaded and the full-screen player isn't
 * open. A tap or an upward swipe reopens the full-screen player.
 *
 * @param onClose the bar's close button. Call [MediaSDK.reset] from it.
 */
@Composable
fun MiniPlayer(playback: Playback, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = playback.hasStream && !playback.isPlayerOpen, modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            factory = { context ->
                SDKMiniPlayerView(context).apply {
                    onExpandListener = { MediaSDK.launchPlayer(context) }
                }
            },
            update = { bar -> bar.onCloseClickListener = onClose },
        )
    }
}
