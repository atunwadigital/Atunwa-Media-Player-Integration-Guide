package com.atunwadigital.examples.atunwa_flutter_example

import android.app.Activity
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.models.SDKState
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The bridge from flutter/docs/getting-started.md, extended with what this example needs:
 * `initialize` returns the station, `load` starts the active station's stream, and the event
 * channel also reports the active station.
 */
class AtunwaMediaBridge(
    private val activity: Activity,
    messenger: BinaryMessenger,
) : MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

    private val scope = MainScope()
    private val context get() = activity.applicationContext
    private var stateJob: Job? = null

    init {
        MethodChannel(messenger, "atunwa/media").setMethodCallHandler(this)
        EventChannel(messenger, "atunwa/media/state").setStreamHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "setup" -> scope.launch {
                MediaSDK.setup(context)
                    .onSuccess { result.success(null) }
                    .onFailure { result.error("setup", it.message, null) }
            }
            "initialize" -> scope.launch {
                MediaSDK.initialize(context, call.argument<String>("apiKey")!!)
                    .onSuccess { result.success(MediaSDK.playerConfig?.toMap()) }
                    .onFailure { result.error("initialize", it.message, null) }
            }
            "activeStation" -> result.success(MediaSDK.playerConfig?.toMap())
            "load" -> {
                if (MediaSDK.currentMediaItem == null) MediaSDK.load()
                result.success(null)
            }
            "launchPlayer" -> {
                MediaSDK.launchPlayer(activity, call.argument<String>("apiKey")!!)
                result.success(null)
            }
            "openPlayer" -> {
                MediaSDK.launchPlayer(activity)
                result.success(null)
            }
            "initializePublisher" -> scope.launch {
                MediaSDK.initializePublisher(context, call.argument<String>("sdkKey")!!)
                    .onSuccess { config -> result.success(config.toMap()) }
                    .onFailure { result.error("initializePublisher", it.message, null) }
            }
            "refreshPublisher" -> scope.launch {
                MediaSDK.refreshPublisherConfig(context, call.argument<String>("sdkKey")!!)
                    .onSuccess { config -> result.success(config.toMap()) }
                    .onFailure { result.error("refreshPublisher", it.message, null) }
            }
            "loadStation" -> withStation(call, result) {
                MediaSDK.loadPlayer(activity, it)
            }
            "activateStation" -> withStation(call, result) {
                MediaSDK.activatePlayer(context, it)
            }
            "play" -> { MediaSDK.play(); result.success(null) }
            "pause" -> { MediaSDK.pause(); result.success(null) }
            "reset" -> { MediaSDK.reset(); result.success(null) }
            else -> result.notImplemented()
        }
    }

    /** Sends `{state, playWhenReady, activeAssetKey, isPlayerOpen}` whenever any of them changes. */
    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        stateJob = scope.launch {
            combine(
                MediaSDK.state,
                MediaSDK.playWhenReady,
                MediaSDK.currentMediaItemFlow,
                MediaSDK.isDedicatedPlayerActive,
            ) { state, playWhenReady, _, isPlayerOpen ->
                mapOf(
                    "state" to state.wireName(),
                    "playWhenReady" to playWhenReady,
                    "activeAssetKey" to MediaSDK.playerConfig?.assetKey,
                    "isPlayerOpen" to isPlayerOpen,
                )
            }.collect { events.success(it) }
        }
    }

    override fun onCancel(arguments: Any?) {
        stateJob?.cancel()
        stateJob = null
    }

    /** Looks a station up by asset key in the loaded publisher config. */
    private fun withStation(
        call: MethodCall,
        result: MethodChannel.Result,
        action: (PlayerConfig) -> Unit,
    ) {
        val assetKey = call.argument<String>("assetKey")
        val station = MediaSDK.publisherConfig?.players?.firstOrNull { it.assetKey == assetKey }
        if (station == null) {
            result.error("unknownStation", "No station $assetKey; call initializePublisher first", null)
            return
        }
        action(station)
        result.success(null)
    }

    private fun com.atunwadigital.mediasdk.models.PublisherConfig.toMap() = mapOf(
        "name" to publisher.name,
        "logo" to publisher.logo,
        "stations" to players.map { it.toMap() },
    )

    private fun PlayerConfig.toMap() = mapOf(
        "assetKey" to assetKey,
        "name" to playerName,
        "logo" to logo,
        "isVideo" to (streamChannels?.isVideo == true),
        "publisherName" to publisher?.name,
    )

    /** AD_PLAYING → "adPlaying", matching iOS's SDKState raw values. */
    private fun SDKState.wireName() =
        name.lowercase().replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
