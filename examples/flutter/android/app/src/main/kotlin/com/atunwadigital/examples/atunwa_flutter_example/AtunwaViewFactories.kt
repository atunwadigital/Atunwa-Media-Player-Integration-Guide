package com.atunwadigital.examples.atunwa_flutter_example

import android.app.Activity
import android.content.Context
import android.view.View
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

// From flutter/docs/native-views.md.

private class WrappedView(private val view: View) : PlatformView {
    override fun getView() = view
    override fun dispose() {}
}

class MiniPlayerFactory(private val activity: Activity) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val view = SDKMiniPlayerView(context).apply {
            onExpandListener = { MediaSDK.launchPlayer(activity) }
            // reset() clears the active station and publisher config. The Dart screens
            // initialize again before the next play.
            onCloseClickListener = { MediaSDK.reset() }
        }
        return WrappedView(view)
    }
}

class LiveCardFactory(private val activity: Activity) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val view = SDKLiveCardView(context).apply {
            previewPlayer = MediaSDK.publisherConfig?.players?.firstOrNull()
            onCardClickListener = {
                val active = MediaSDK.playerConfig
                val preview = previewPlayer
                when {
                    active != null -> MediaSDK.launchPlayer(activity)
                    preview != null -> MediaSDK.loadPlayer(activity, preview)
                }
            }
        }
        return WrappedView(view)
    }
}
