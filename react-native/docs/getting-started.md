# Getting started with React Native

This guide adds the native SDKs to a React Native app and wires up a native module so JavaScript
can launch the player, control playback and observe state. You copy a few native files and one
TypeScript file.

- [1. Android setup](#1-android-setup)
- [2. iOS setup](#2-ios-setup)
- [3. The JavaScript API](#3-the-javascript-api)
- [4. The Android module](#4-the-android-module)
- [5. The iOS module](#5-the-ios-module)
- [6. Use it](#6-use-it)

Throughout, replace `com.example.myapp` with your app's package name and `MyApp` with your iOS
project's name. Using Expo? Read [Expo](expo.md) first.

> **New Architecture.** The module below uses React Native's classic native module API. In the New
> Architecture, React Native loads modules written this way through its interop layer, so it works
> with or without the New Architecture and needs no codegen. If you'd rather write it as a Turbo
> Module, keep the same method names and argument types.

---

## 1. Android setup

### 1.1 Add the repository

```groovy
// android/build.gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url "https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven"
            content {
                includeGroup "com.atunwadigital"
                includeGroup "com.adswizz"
            }
        }
    }
}
```

### 1.2 Add the dependency

```groovy
// android/app/build.gradle
dependencies {
    implementation "com.atunwadigital:mediasdk:<version>"   // latest version: see Releases
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
}
```

The SDK needs `minSdkVersion` 24, which is already the default in current React Native templates.
If your app sets it lower in `android/build.gradle`, raise it to 24.

### 1.3 Release builds

If you've turned on `enableProguardInReleaseBuilds`, add the SDK's keep rules from
[Android Getting started → Code shrinking](../../android/docs/getting-started.md#6-code-shrinking)
to `android/app/proguard-rules.pro`.

The SDK's manifest merges its service, player activity and permissions into your app, so you don't
change `AndroidManifest.xml`.

## 2. iOS setup

### 2.1 Add the Swift package to your app target

1. Run `pod install` in `ios/`, then open `ios/MyApp.xcworkspace` in Xcode.
2. Choose **File → Add Package Dependencies…** and enter
   `https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git`.
3. Add the **MediaSDK** library to the **MyApp** app target.

The module lives in the app target too, so it can `import MediaSDK` directly. The SDK is
distributed through Swift Package Manager only, so it can't be added from your `Podfile`; adding it
to the app target alongside your pods works fine.

### 2.2 Capabilities

- The SDK needs **iOS 15.0** or later. Current React Native templates already target 15.1.
- Under **MyApp → Signing & Capabilities**, add **Background Modes** and tick
  **Audio, AirPlay, and Picture in Picture**.
- For full-screen video, allow the landscape orientations under **MyApp → General → Device
  Orientation**.
- For Ad Manager banners, add `GADApplicationIdentifier` to `Info.plist`. See
  [iOS Getting started → Info.plist](../../ios/docs/getting-started.md#2-infoplist).

## 3. The JavaScript API

Create `src/atunwaMedia.ts`. This is the only file the rest of your app imports.

```ts
// src/atunwaMedia.ts
import { useEffect, useState } from 'react';
import { NativeEventEmitter, NativeModules } from 'react-native';

const Native = NativeModules.AtunwaMedia;
const emitter = new NativeEventEmitter(Native);

export type AtunwaState =
  | 'idle' | 'initializing' | 'loading' | 'ready' | 'playing'
  | 'paused' | 'buffering' | 'error' | 'adPlaying';

export type AtunwaStation = {
  assetKey: string;
  name: string;
  logo: string;
  isVideo: boolean;
};

export const AtunwaMedia = {
  /** Call once at startup, before anything else. */
  setup: (): Promise<void> => Native.setup(),

  // Single-player mode
  initializePlayer: (apiKey: string): Promise<void> => Native.initializePlayer(apiKey),
  launchPlayer: (apiKey: string): Promise<void> => Native.launchPlayer(apiKey),
  /** Opens the full-screen player for whatever is active. */
  openPlayer: (): Promise<void> => Native.openPlayer(),

  // Publisher mode
  initializePublisher: (sdkKey: string): Promise<AtunwaStation[]> =>
    Native.initializePublisher(sdkKey),
  /** Activates a station and opens the full-screen player. */
  loadStation: (assetKey: string): Promise<void> => Native.loadStation(assetKey),
  /** Activates a station and starts playing it without opening the player. */
  activateStation: (assetKey: string): Promise<void> => Native.activateStation(assetKey),

  // Playback
  play: (): Promise<void> => Native.play(),
  pause: (): Promise<void> => Native.pause(),
  reset: (): Promise<void> => Native.reset(),

  /** Calls `listener` with every state change. Returns a function that unsubscribes. */
  addStateListener(listener: (state: AtunwaState) => void): () => void {
    const subscription = emitter.addListener('AtunwaMediaState', listener);
    return () => subscription.remove();
  },
};

/** The SDK's current playback state, kept up to date. */
export function useAtunwaState(): AtunwaState {
  const [state, setState] = useState<AtunwaState>('idle');
  useEffect(() => AtunwaMedia.addStateListener(setState), []);
  return state;
}
```

Failures from the native side reject the promise with an error whose `code` names the call that
failed, for example `initializePublisher`.

## 4. The Android module

Create `android/app/src/main/java/com/example/myapp/atunwa/AtunwaMediaModule.kt`:

```kotlin
package com.example.myapp.atunwa

import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.models.PlayerConfig
import com.atunwadigital.mediasdk.models.SDKState
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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
                .onSuccess { promise.resolve(null) }
                .onFailure { promise.reject("initializePlayer", it) }
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
                .onSuccess { config ->
                    val stations = Arguments.createArray()
                    config.players.forEach { stations.pushMap(it.toMap()) }
                    promise.resolve(stations)
                }
                .onFailure { promise.reject("initializePublisher", it) }
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

    // Required by NativeEventEmitter. State updates run only while JavaScript is listening.

    @ReactMethod
    fun addListener(eventName: String) {
        if (listenerCount++ > 0) return
        stateJob = scope.launch {
            MediaSDK.state.collect { state ->
                reactApplicationContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("AtunwaMediaState", state.wireName())
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

    private fun PlayerConfig.toMap() = Arguments.createMap().apply {
        putString("assetKey", assetKey)
        putString("name", playerName)
        putString("logo", logo)
        putBoolean("isVideo", streamChannels?.isVideo == true)
    }

    /** AD_PLAYING → "adPlaying", matching iOS's SDKState raw values. */
    private fun SDKState.wireName() =
        name.lowercase().replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
```

Create `AtunwaMediaPackage.kt` next to it:

```kotlin
package com.example.myapp.atunwa

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class AtunwaMediaPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(AtunwaMediaModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
```

Then add the package to the list in `MainApplication.kt`:

```kotlin
override fun getPackages(): List<ReactPackage> =
    PackageList(this).packages.apply {
        add(AtunwaMediaPackage())
    }
```

## 5. The iOS module

Add these three files to the **MyApp** target in Xcode. If Xcode offers to create a bridging
header when you add the first Swift file, accept, and use it for the first file below. If your app
already has one, add the imports to it.

```objc
// ios/MyApp/MyApp-Bridging-Header.h
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTViewManager.h>
```

```objc
// ios/MyApp/AtunwaMedia.m
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(AtunwaMedia, RCTEventEmitter)

RCT_EXTERN_METHOD(setup:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(initializePlayer:(NSString *)apiKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(launchPlayer:(NSString *)apiKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(openPlayer:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(initializePublisher:(NSString *)sdkKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(loadStation:(NSString *)assetKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(activateStation:(NSString *)assetKey resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(play:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(pause:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(reset:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
```

```swift
// ios/MyApp/AtunwaMedia.swift
import Combine
import Foundation
import MediaSDK

@objc(AtunwaMedia)
final class AtunwaMedia: RCTEventEmitter {

    private var stateSubscription: AnyCancellable?

    override static func requiresMainQueueSetup() -> Bool { true }
    override func supportedEvents() -> [String]! { ["AtunwaMediaState"] }

    @objc func setup(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { _ = MediaSDK.shared }   // the first access creates the player and ad managers
    }

    @objc func initializePlayer(_ apiKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                try await MediaSDK.shared.initialize(apiKey: apiKey)
                resolve(nil)
            } catch {
                reject("initializePlayer", "\(error)", error)
            }
        }
    }

    @objc func launchPlayer(_ apiKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.launchPlayer(apiKey: apiKey) }
    }

    @objc func openPlayer(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.launchPlayer() }
    }

    @objc func initializePublisher(_ sdkKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                let config = try await MediaSDK.shared.initializePublisher(sdkKey: sdkKey)
                resolve(config.players.map(Self.toMap))
            } catch {
                reject("initializePublisher", "\(error)", error)
            }
        }
    }

    @objc func loadStation(_ assetKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        withStation(assetKey, resolve, reject) { MediaSDK.shared.loadPlayer($0) }
    }

    @objc func activateStation(_ assetKey: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        withStation(assetKey, resolve, reject) { MediaSDK.shared.activatePlayer($0) }
    }

    @objc func play(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.play() }
    }

    @objc func pause(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.pause() }
    }

    @objc func reset(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        onMain(resolve) { MediaSDK.shared.reset() }
    }

    // MARK: Events. State updates run only while JavaScript is listening.

    // React Native calls these on the module's own queue, so hop to the main queue, in order.

    override func startObserving() {
        DispatchQueue.main.async {
            MainActor.assumeIsolated {
                self.stateSubscription = MediaSDK.shared.statePublisher
                    .receive(on: DispatchQueue.main)
                    .sink { [weak self] in self?.sendEvent(withName: "AtunwaMediaState", body: $0.rawValue) }
            }
        }
    }

    override func stopObserving() {
        DispatchQueue.main.async { self.stateSubscription = nil }
    }

    // MARK: Helpers

    private func onMain(_ resolve: @escaping RCTPromiseResolveBlock, _ action: @escaping @MainActor () -> Void) {
        Task { @MainActor in
            action()
            resolve(nil)
        }
    }

    /// Looks a station up by asset key in the loaded publisher config.
    private func withStation(
        _ assetKey: String,
        _ resolve: @escaping RCTPromiseResolveBlock,
        _ reject: @escaping RCTPromiseRejectBlock,
        _ action: @escaping @MainActor (PlayerConfig) -> Void
    ) {
        Task { @MainActor in
            guard let station = MediaSDK.shared.publisherConfig?.players.first(where: { $0.assetKey == assetKey }) else {
                reject("unknownStation", "No station \(assetKey); call initializePublisher first", nil)
                return
            }
            action(station)
            resolve(nil)
        }
    }

    private static func toMap(_ station: PlayerConfig) -> [String: Any] {
        [
            "assetKey": station.assetKey,
            "name": station.playerName,
            "logo": station.logo,
            "isVideo": station.streamChannels?.isVideo ?? false,
        ]
    }
}
```

`launchPlayer` and `loadPlayer` are called without a `from:` view controller, so the SDK presents
its player over whatever is on screen, which is your React Native root view controller.

## 6. Use it

Call `setup()` once when the app starts, before anything else:

```tsx
// App.tsx
import { useEffect, useState } from 'react';
import { ActivityIndicator } from 'react-native';
import { AtunwaMedia } from './src/atunwaMedia';

export default function App() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    AtunwaMedia.setup().finally(() => setReady(true));
  }, []);

  return ready ? <HomeScreen /> : <ActivityIndicator />;
}
```

**Single station:**

```tsx
<Button title="Listen live" onPress={() => AtunwaMedia.launchPlayer('YOUR_PLAYER_API_KEY')} />
```

**A publisher's stations, listed in React Native:**

```tsx
import { useEffect, useState } from 'react';
import { FlatList, Image, Pressable, Text } from 'react-native';
import { AtunwaMedia, type AtunwaStation } from './src/atunwaMedia';

export function StationList() {
  const [stations, setStations] = useState<AtunwaStation[]>([]);

  useEffect(() => {
    AtunwaMedia.initializePublisher('YOUR_PUBLISHER_SDK_KEY').then(setStations);
  }, []);

  return (
    <FlatList
      data={stations}
      keyExtractor={(station) => station.assetKey}
      renderItem={({ item }) => (
        <Pressable onPress={() => AtunwaMedia.loadStation(item.assetKey)}>
          {item.logo ? <Image source={{ uri: item.logo }} style={{ width: 40, height: 40 }} /> : null}
          <Text>{item.name}</Text>
          <Text>{item.isVideo ? 'TV' : 'Radio'}</Text>
        </Pressable>
      )}
    />
  );
}
```

**Playback state:**

```tsx
import { ActivityIndicator, Button } from 'react-native';
import { AtunwaMedia, useAtunwaState } from './src/atunwaMedia';

export function PlayPauseButton() {
  const state = useAtunwaState();

  switch (state) {
    case 'playing':
      return <Button title="Pause" onPress={AtunwaMedia.pause} />;
    case 'paused':
      return <Button title="Play" onPress={AtunwaMedia.play} />;
    case 'buffering':
    case 'loading':
    case 'adPlaying':
      return <ActivityIndicator />;
    default:
      return null;
  }
}
```

The states and what triggers each one are described in Media playback
([iOS](../../ios/docs/media-playback.md), [Android](../../android/docs/media-playback.md)).

## Extending the module

The module covers the calls most apps need. To expose more of the SDK, such as `fetchNews`,
`adPlaybackState` or `isDedicatedPlayerActive`, add a method to each platform's module (and to
`AtunwaMedia.m` on iOS) or another event name, then a matching function in `atunwaMedia.ts`. The
[iOS](../../ios/docs/api-reference.md) and [Android](../../android/docs/api-reference.md) API
references list everything available. Keep the method names and the shape of the values the same on
both platforms, so your JavaScript doesn't need to know which one it's on.

---

**Next:** [Native views](native-views.md) · [Troubleshooting](troubleshooting.md)
