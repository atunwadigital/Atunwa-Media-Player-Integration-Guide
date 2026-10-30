# Getting started with Flutter

This guide adds the native SDKs to a Flutter app and wires up a bridge so Dart can launch the
player, control playback and observe state. It takes three files of native code and one Dart
file, all of which you can copy as they are.

- [1. Android setup](#1-android-setup)
- [2. iOS setup](#2-ios-setup)
- [3. The Dart API](#3-the-dart-api)
- [4. The Android bridge](#4-the-android-bridge)
- [5. The iOS bridge](#5-the-ios-bridge)
- [6. Use it](#6-use-it)

Throughout, replace `com.example.myapp` with your app's package name.

---

## 1. Android setup

### 1.1 Add the repository

Flutter apps declare repositories per project, so add the Atunwa Maven repository to `allprojects`
in `android/build.gradle.kts`:

```kotlin
// android/build.gradle.kts
allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://raw.githubusercontent.com/atunwadigital/Atunwa-Media-Player-Integration-Guide/maven") {
            content {
                includeGroup("com.atunwadigital")
                includeGroup("com.adswizz")
            }
        }
    }
}
```

Don't set `RepositoriesMode.FAIL_ON_PROJECT_REPOS` in `settings.gradle.kts`: Flutter plugins
declare their own repositories, and that mode would reject them.

### 1.2 Add the dependency and raise `minSdk`

```kotlin
// android/app/build.gradle.kts
android {
    defaultConfig {
        minSdk = 24   // the SDK needs Android 7.0+
    }
}

dependencies {
    implementation("com.atunwadigital:mediasdk:<version>")   // latest version: see Releases
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

The coroutines dependency is for the bridge's own code, which calls the SDK's `suspend` functions.

### 1.3 Release builds

Flutter shrinks release builds with R8 by default. Add the SDK's keep rules from
[Android Getting started → Code shrinking](../../android/docs/getting-started.md#6-code-shrinking)
to `android/app/proguard-rules.pro`, and make sure that file is listed in `proguardFiles` for your
release build type.

The SDK's manifest merges its service, player activity and permissions into your app, so you don't
change `AndroidManifest.xml`.

## 2. iOS setup

### 2.1 Add the Swift package to the Runner target

1. Open `ios/Runner.xcworkspace` in Xcode.
2. Choose **File → Add Package Dependencies…** and enter
   `https://github.com/atunwadigital/Atunwa-Media-Player-Integration-Guide.git`.
3. Add the **MediaSDK** library to the **Runner** target.

The bridge code lives in the Runner target too, so it can `import MediaSDK` directly. You don't
need a podspec, and this works whether or not your other plugins use CocoaPods.

### 2.2 Deployment target and background audio

- Set the deployment target to **iOS 15.0** in **Runner → General → Minimum Deployments**. If your
  `ios/Podfile` has a `platform :ios` line, set it to `'15.0'` too.
- Under **Runner → Signing & Capabilities**, add **Background Modes** and tick
  **Audio, AirPlay, and Picture in Picture**.
- For full-screen video, allow the landscape orientations under **Runner → General → Device
  Orientation**.
- For Ad Manager banners, add `GADApplicationIdentifier` to `ios/Runner/Info.plist`. See
  [iOS Getting started → Info.plist](../../ios/docs/getting-started.md#2-infoplist).

## 3. The Dart API

Create `lib/atunwa_media.dart`. This is the only file your Flutter code imports.

```dart
// lib/atunwa_media.dart
import 'package:flutter/services.dart';

/// A station from a publisher's station list.
class AtunwaStation {
  const AtunwaStation({
    required this.assetKey,
    required this.name,
    required this.logo,
    required this.isVideo,
  });

  factory AtunwaStation.fromMap(Map<Object?, Object?> map) => AtunwaStation(
        assetKey: map['assetKey'] as String,
        name: map['name'] as String,
        logo: map['logo'] as String? ?? '',
        isVideo: map['isVideo'] as bool? ?? false,
      );

  final String assetKey;
  final String name;
  final String logo;
  final bool isVideo;
}

/// Calls the native Atunwa Media SDK through the app's platform channels.
class AtunwaMedia {
  static const _methods = MethodChannel('atunwa/media');
  static const _events = EventChannel('atunwa/media/state');

  /// Playback state: idle, initializing, loading, ready, playing, paused,
  /// buffering, error or adPlaying.
  static final Stream<String> state =
      _events.receiveBroadcastStream().cast<String>();

  /// Call once at startup, before anything else.
  static Future<void> setup() => _methods.invokeMethod('setup');

  // Single-player mode
  static Future<void> initialize(String apiKey) =>
      _methods.invokeMethod('initialize', {'apiKey': apiKey});
  static Future<void> launchPlayer(String apiKey) =>
      _methods.invokeMethod('launchPlayer', {'apiKey': apiKey});

  /// Opens the full-screen player for whatever is active.
  static Future<void> openPlayer() => _methods.invokeMethod('openPlayer');

  // Publisher mode
  static Future<List<AtunwaStation>> initializePublisher(String sdkKey) async {
    final list = await _methods.invokeListMethod<Map<Object?, Object?>>(
        'initializePublisher', {'sdkKey': sdkKey});
    return (list ?? []).map(AtunwaStation.fromMap).toList();
  }

  /// Activates a station and opens the full-screen player.
  static Future<void> loadStation(String assetKey) =>
      _methods.invokeMethod('loadStation', {'assetKey': assetKey});

  /// Activates a station and starts playing it without opening the player.
  static Future<void> activateStation(String assetKey) =>
      _methods.invokeMethod('activateStation', {'assetKey': assetKey});

  // Playback
  static Future<void> play() => _methods.invokeMethod('play');
  static Future<void> pause() => _methods.invokeMethod('pause');
  static Future<void> reset() => _methods.invokeMethod('reset');
}
```

Failures from the native side arrive as a `PlatformException` whose `code` names the call that
failed, for example `initializePublisher`.

## 4. The Android bridge

Create `android/app/src/main/kotlin/com/example/myapp/AtunwaMediaBridge.kt`:

```kotlin
package com.example.myapp

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
import kotlinx.coroutines.launch

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
                    .onSuccess { result.success(null) }
                    .onFailure { result.error("initialize", it.message, null) }
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
                    .onSuccess { config -> result.success(config.players.map { it.toMap() }) }
                    .onFailure { result.error("initializePublisher", it.message, null) }
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

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        stateJob = scope.launch {
            MediaSDK.state.collect { events.success(it.wireName()) }
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

    private fun PlayerConfig.toMap() = mapOf(
        "assetKey" to assetKey,
        "name" to playerName,
        "logo" to logo,
        "isVideo" to (streamChannels?.isVideo == true),
    )

    /** AD_PLAYING → "adPlaying", matching iOS's SDKState raw values. */
    private fun SDKState.wireName() =
        name.lowercase().replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }
}
```

Then create the bridge in `MainActivity.kt`:

```kotlin
package com.example.myapp

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        AtunwaMediaBridge(this, flutterEngine.dartExecutor.binaryMessenger)
    }
}
```

## 5. The iOS bridge

In Xcode, add a new Swift file `AtunwaMediaBridge.swift` to the **Runner** target:

```swift
// ios/Runner/AtunwaMediaBridge.swift
import Combine
import Flutter
import MediaSDK

final class AtunwaMediaBridge: NSObject, FlutterStreamHandler {

    private var stateSubscription: AnyCancellable?

    static func register(with registrar: FlutterPluginRegistrar) {
        let bridge = AtunwaMediaBridge()
        FlutterMethodChannel(name: "atunwa/media", binaryMessenger: registrar.messenger())
            .setMethodCallHandler { call, result in
                Task { @MainActor in await bridge.handle(call, result) }
            }
        FlutterEventChannel(name: "atunwa/media/state", binaryMessenger: registrar.messenger())
            .setStreamHandler(bridge)
    }

    @MainActor
    private func handle(_ call: FlutterMethodCall, _ result: @escaping FlutterResult) async {
        let args = call.arguments as? [String: Any] ?? [:]
        let sdk = MediaSDK.shared   // the first access creates the player and ad managers

        do {
            switch call.method {
            case "setup":
                result(nil)
            case "initialize":
                try await sdk.initialize(apiKey: args["apiKey"] as? String ?? "")
                result(nil)
            case "launchPlayer":
                sdk.launchPlayer(apiKey: args["apiKey"] as? String ?? "")
                result(nil)
            case "openPlayer":
                sdk.launchPlayer()
                result(nil)
            case "initializePublisher":
                let config = try await sdk.initializePublisher(sdkKey: args["sdkKey"] as? String ?? "")
                result(config.players.map(Self.toMap))
            case "loadStation", "activateStation":
                let assetKey = args["assetKey"] as? String
                guard let station = sdk.publisherConfig?.players.first(where: { $0.assetKey == assetKey }) else {
                    result(FlutterError(code: "unknownStation",
                                        message: "No station \(assetKey ?? ""); call initializePublisher first",
                                        details: nil))
                    return
                }
                if call.method == "loadStation" { sdk.loadPlayer(station) } else { sdk.activatePlayer(station) }
                result(nil)
            case "play":
                sdk.play(); result(nil)
            case "pause":
                sdk.pause(); result(nil)
            case "reset":
                sdk.reset(); result(nil)
            default:
                result(FlutterMethodNotImplemented)
            }
        } catch {
            result(FlutterError(code: call.method, message: "\(error)", details: nil))
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

    // MARK: FlutterStreamHandler

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        MainActor.assumeIsolated {
            stateSubscription = MediaSDK.shared.statePublisher
                .receive(on: DispatchQueue.main)
                .sink { events($0.rawValue) }
        }
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        stateSubscription = nil
        return nil
    }
}
```

`launchPlayer` and `loadPlayer` are called without a `from:` view controller, so the SDK presents
its player over whatever is on screen, which is your `FlutterViewController`.

Register the bridge in `AppDelegate.swift`:

```swift
import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate {
    override func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        GeneratedPluginRegistrant.register(with: self)
        AtunwaMediaBridge.register(with: registrar(forPlugin: "AtunwaMediaBridge")!)
        return super.application(application, didFinishLaunchingWithOptions: launchOptions)
    }
}
```

If your app uses the newer scene-based template, where `AppDelegate` implements
`didInitializeImplicitFlutterEngine(_:)`, register it there instead:

```swift
func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    AtunwaMediaBridge.register(with: engineBridge.pluginRegistry.registrar(forPlugin: "AtunwaMediaBridge")!)
}
```

## 6. Use it

Call `setup()` once before `runApp`, then use the rest from anywhere:

```dart
import 'package:flutter/material.dart';
import 'atunwa_media.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AtunwaMedia.setup();
  runApp(const MyApp());
}
```

**Single station:**

```dart
ElevatedButton(
  onPressed: () => AtunwaMedia.launchPlayer('YOUR_PLAYER_API_KEY'),
  child: const Text('Listen live'),
)
```

**A publisher's stations, listed in Flutter:**

```dart
class StationList extends StatelessWidget {
  const StationList({super.key});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
      future: AtunwaMedia.initializePublisher('YOUR_PUBLISHER_SDK_KEY'),
      builder: (context, snapshot) {
        final stations = snapshot.data ?? [];
        return ListView(
          children: [
            for (final station in stations)
              ListTile(
                leading: station.logo.isEmpty ? null : Image.network(station.logo, width: 40),
                title: Text(station.name),
                subtitle: Text(station.isVideo ? 'TV' : 'Radio'),
                onTap: () => AtunwaMedia.loadStation(station.assetKey),
              ),
          ],
        );
      },
    );
  }
}
```

**Playback state:**

```dart
StreamBuilder<String>(
  stream: AtunwaMedia.state,
  builder: (context, snapshot) => switch (snapshot.data) {
    'playing' => IconButton(icon: const Icon(Icons.pause), onPressed: AtunwaMedia.pause),
    'paused' => IconButton(icon: const Icon(Icons.play_arrow), onPressed: AtunwaMedia.play),
    'buffering' || 'loading' || 'adPlaying' => const CircularProgressIndicator(),
    _ => const SizedBox.shrink(),
  },
)
```

The states and what triggers each one are described in Media playback
([iOS](../../ios/docs/media-playback.md), [Android](../../android/docs/media-playback.md)).

## Extending the bridge

The bridge covers the calls most apps need. To expose more of the SDK, such as `fetchNews`,
`adPlaybackState` or `isDedicatedPlayerActive`, add a `case` to each platform's handler (or another
event channel for a stream) and a matching method to `AtunwaMedia`. The
[iOS](../../ios/docs/api-reference.md) and [Android](../../android/docs/api-reference.md) API
references list everything available. Keep the method names and the shape of the values the same on
both platforms, so the Dart side doesn't need to know which one it's on.

---

**Next:** [Native views](native-views.md) · [Troubleshooting](troubleshooting.md)
