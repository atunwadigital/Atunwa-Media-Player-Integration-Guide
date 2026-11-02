package com.atunwadigital.examples.mediahouse.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.ui.SDKBannerAdView
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
import kotlinx.coroutines.delay

// The SDK's views are Android Views; these wrap them for Compose. They observe the SDK on their
// own, so nothing here pushes playback state into them.

/**
 * The SDK's "now playing" card: live video for TV stations, a waveform for radio, and the
 * station's ads. It shows whatever is playing, or [previewPlayer] until something is.
 */
@Composable
fun LiveCard(previewPlayer: PlayerConfig?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { SDKLiveCardView(it) },
        update = { card ->
            card.previewPlayer = previewPlayer
            card.onCardClickListener = onClick
        },
    )
}

/** The SDK's mini player bar. A tap or an upward swipe reopens the full-screen player. */
@Composable
fun MiniPlayer(visible: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                SDKMiniPlayerView(context).apply {
                    onExpandListener = { MediaSDK.launchPlayer(context) }
                }
            },
            update = { bar -> bar.onCloseClickListener = onClose },
        )
    }
}

/**
 * A Google Ad Manager banner in a feed, through the SDK's banner view (with Amazon APS bidding
 * when configured). It takes no space until an ad fills.
 */
@Composable
fun InFeedBanner(adUnitId: String, modifier: Modifier = Modifier) {
    var hasAd by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<SDKBannerAdView?>(null) }
    // SDKBannerAdView drops a request made before Google Mobile Ads has finished starting, so wait
    // for it. It usually takes a moment after launch.
    LaunchedEffect(banner, adUnitId) {
        val view = banner ?: return@LaunchedEffect
        while (MediaSDK.gptManager?.isAdsEnabled != true) delay(250)
        view.loadAd(adUnitId)
    }
    AndroidView(
        // Collapsed (but still laid out, so it can load) until an ad fills.
        modifier = modifier.fillMaxWidth().then(if (hasAd) Modifier else Modifier.height(1.dp).alpha(0f)),
        factory = { context ->
            SDKBannerAdView(context).apply {
                onContentStateChanged = { hasAd = it }
                banner = this
            }
        },
        onRelease = { it.destroy() },
    )
}
