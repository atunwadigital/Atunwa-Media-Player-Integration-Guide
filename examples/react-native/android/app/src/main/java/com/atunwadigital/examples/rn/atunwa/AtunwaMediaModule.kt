package com.atunwadigital.examples.rn.atunwa

import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.models.PublisherConfig
import com.atunwadigital.mediasdk.models.SDKState
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The module from react-native/docs/getting-started.md, extended with what this example needs:
 * `initializePlayer` resolves with the station, `load` starts the active station's stream, and
 * the event also reports the active station and whether the full-screen player is open.
 */
class AtunwaMediaModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val scope = MainScope()
    private val context get() = reactApplicationContext.applicationContext
    private var stateJob: Job? = null
    private var listenerCount = 0

    override fun getName() = "AtunwaMedia"

    @ReactMethod
    fun setup(promise: Promise) {
        scope.launch {
            MediaSDK.setup(context)
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("setup", it) }
        }
    }

    @ReactMethod
    fun initializePlayer(apiKey: String, promise: Promise) {
        scope.launch {
            MediaSDK.initialize(context, apiKey)
                .onSuccess { promise.resolve(MediaSDK.playerConfig?.toMap()) }
                .onFailure { promise.reject("initializePlayer", it) }
        }
    }

    @ReactMethod
    fun activeStation(promise: Promise) {
        scope.launch { promise.resolve(MediaSDK.playerConfig?.toMap()) }
    }

    @ReactMethod
    fun load(promise: Promise) {
        scope.launch {
            if (MediaSDK.currentMediaItem == null) MediaSDK.load()
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun launchPlayer(apiKey: String, promise: Promise) = withActivity(promise) {
        MediaSDK.launchPlayer(it, apiKey)
    }

    @ReactMethod
    fun openPlayer(promise: Promise) = withActivity(promise) {
        MediaSDK.launchPlayer(it)
    }

    @ReactMethod
    fun initializePublisher(sdkKey: String, promise: Promise) {
        scope.launch {
            MediaSDK.initializePublisher(context, sdkKey)
                .onSuccess { promise.resolve(it.toMap()) }
                .onFailure { promise.reject("initializePublisher", it) }
        }
    }

    @ReactMethod
    fun refreshPublisher(sdkKey: String, promise: Promise) {
        scope.launch {
            MediaSDK.refreshPublisherConfig(context, sdkKey)
                .onSuccess { promise.resolve(it.toMap()) }
                .onFailure { promise.reject("refreshPublisher", it) }
        }
    }

    @ReactMethod
    fun loadStation(assetKey: String, promise: Promise) {
        scope.launch {
            val station = findStation(assetKey, promise) ?: return@launch
            val activity = reactApplicationContext.currentActivity
                ?: return@launch promise.reject("noActivity", "No foreground Activity to open the player from")
            MediaSDK.loadPlayer(activity, station)
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun activateStation(assetKey: String, promise: Promise) {
        scope.launch {
            val station = findStation(assetKey, promise) ?: return@launch
            MediaSDK.activatePlayer(context, station)
            promise.resolve(null)
        }
    }

    @ReactMethod
    fun play(promise: Promise) { scope.launch { MediaSDK.play(); promise.resolve(null) } }

    @ReactMethod
    fun pause(promise: Promise) { scope.launch { MediaSDK.pause(); promise.resolve(null) } }

    @ReactMethod
    fun reset(promise: Promise) { scope.launch { MediaSDK.reset(); promise.resolve(null) } }

    // Required by NativeEventEmitter. Updates run only while JavaScript is listening.

    @ReactMethod
    fun addListener(eventName: String) {
        if (listenerCount++ > 0) return
        stateJob = scope.launch {
            combine(
                MediaSDK.state,
                MediaSDK.playWhenReady,
                MediaSDK.currentMediaItemFlow,
                MediaSDK.isDedicatedPlayerActive,
            ) { state, playWhenReady, _, isPlayerOpen ->
                Arguments.createMap().apply {
                    putString("state", state.wireName())
                    putBoolean("playWhenReady", playWhenReady)
                    putString("activeAssetKey", MediaSDK.playerConfig?.assetKey)
                    putBoolean("isPlayerOpen", isPlayerOpen)
                }
            }.collect { playback ->
                reactApplicationContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("AtunwaMediaPlayback", playback)
            }
        }
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount = maxOf(0, listenerCount - count)
        if (listenerCount == 0) {
            stateJob?.cancel()
            stateJob = null
        }
    }

    override fun invalidate() {
        stateJob?.cancel()
        super.invalidate()
    }

    /** Runs `action` on the main thread with the current Activity, which the SDK needs to open its player. */
    private fun withActivity(promise: Promise, action: (android.app.Activity) -> Unit) {
        scope.launch {
            val activity = reactApplicationContext.currentActivity
            if (activity == null) {
                promise.reject("noActivity", "No foreground Activity to open the player from")
                return@launch
            }
            action(activity)
            promise.resolve(null)
        }
    }

    /** Looks a station up by asset key in the loaded publisher config, or rejects `promise`. */
    private fun findStation(assetKey: String, promise: Promise): PlayerConfig? {
        val station = MediaSDK.publisherConfig?.players?.firstOrNull { it.assetKey == assetKey }
        if (station == null) {
            promise.reject("unknownStation", "No station $assetKey; call initializePublisher first")
        }
        return station
    }

    private fun PublisherConfig.toMap(): WritableMap = Arguments.createMap().apply {
        putString("name", publisher.name)
        putString("logo", publisher.logo)
        putArray("stations", Arguments.createArray().also { array -> players.forEach { array.pushMap(it.toMap()) } })
    }

    private fun PlayerConfig.toMap(): WritableMap = Arguments.createMap().apply {
        putString("assetKey", assetKey)
        putString("name", playerName)
        putString("logo", logo)
        putBoolean("isVideo", streamChannels?.isVideo == true)
        putString("publisherName", publisher?.name)
    }

    /** AD_PLAYING → "adPlaying", matching iOS's SDKState raw values. */
    private fun SDKState.wireName() =
        name.lowercase().replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
