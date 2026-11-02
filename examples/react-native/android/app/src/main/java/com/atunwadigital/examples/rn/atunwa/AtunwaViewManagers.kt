package com.atunwadigital.examples.rn.atunwa

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

// From react-native/docs/native-views.md.

/** Re-runs measure and layout when a child asks, which React Native wouldn't do on its own. */
class RelayoutingFrame(context: Context, child: View) : FrameLayout(context) {
    private val relayout = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout(left, top, right, bottom)
    }

    init { addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    override fun requestLayout() {
        super.requestLayout()
        post(relayout)
    }
}

class MiniPlayerManager : SimpleViewManager<FrameLayout>() {
    override fun getName() = "AtunwaMiniPlayer"

    override fun createViewInstance(context: ThemedReactContext): FrameLayout {
        val view = SDKMiniPlayerView(context).apply {
            onExpandListener = { context.currentActivity?.let { MediaSDK.launchPlayer(it) } }
            // reset() clears the active station and publisher config. The JavaScript screens
            // initialize again before the next play.
            onCloseClickListener = { MediaSDK.reset() }
        }
        return RelayoutingFrame(context, view)
    }
}

class LiveCardManager : SimpleViewManager<FrameLayout>() {
    override fun getName() = "AtunwaLiveCard"

    override fun createViewInstance(context: ThemedReactContext): FrameLayout {
        val view = SDKLiveCardView(context)
        view.previewPlayer = MediaSDK.publisherConfig?.players?.firstOrNull()
        view.onCardClickListener = {
            val activity = context.currentActivity
            val preview = view.previewPlayer
            when {
                activity == null -> Unit
                MediaSDK.playerConfig != null -> MediaSDK.launchPlayer(activity)
                preview != null -> MediaSDK.loadPlayer(activity, preview)
            }
        }
        return RelayoutingFrame(context, view)
    }
}
