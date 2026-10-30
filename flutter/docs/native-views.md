# Embedding native views in Flutter

The SDK's live card and mini player are native views. You can place them inside your Flutter
screens with [platform views](https://docs.flutter.dev/platform-integration/platform-views): the
native side registers a factory under a name, and Dart shows it with `AndroidView` or `UiKitView`.

This guide assumes you've set up the bridge from [Getting started](getting-started.md).

> Platform views cost more than ordinary widgets: each one is a real native view composited with
> Flutter's output. A live card at the top of a screen and a mini player at the bottom are fine.
> Don't put SDK views inside list items.

- [Which views to embed](#which-views-to-embed)
- [Android](#android)
- [iOS](#ios)
- [Dart](#dart)

---

## Which views to embed

| View | Embed it? | Notes |
|---|---|---|
| `SDKMiniPlayerView` | Yes | A fixed-height bar. Its callbacks are handled natively below |
| `SDKLiveCardView` | Yes | Always 16:9. Wrap it in an `AspectRatio` |
| Full-screen player | No | Use `AtunwaMedia.openPlayer()`: it's a native screen of its own |
| Banner and companion ad views | Usually no | The live card and the full-screen player already show the station's ads |

Both views observe the SDK on their own, so once they're on screen you don't push any state into
them. See UI components ([iOS](../../ios/docs/ui-components.md),
[Android](../../android/docs/ui-components.md)) for what each one shows.

## Android

Create `android/app/src/main/kotlin/com/example/myapp/AtunwaViewFactories.kt`:

```kotlin
package com.example.myapp

import android.app.Activity
import android.content.Context
import android.view.View
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

private class WrappedView(private val view: View) : PlatformView {
    override fun getView() = view
    override fun dispose() {}
}

class MiniPlayerFactory(private val activity: Activity) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val view = SDKMiniPlayerView(context).apply {
            onExpandListener = { MediaSDK.launchPlayer(activity) }
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
```

Register the factories next to the bridge in `MainActivity.kt`:

```kotlin
override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)
    AtunwaMediaBridge(this, flutterEngine.dartExecutor.binaryMessenger)

    val registry = flutterEngine.platformViewsController.registry
    registry.registerViewFactory("atunwa/mini_player", MiniPlayerFactory(this))
    registry.registerViewFactory("atunwa/live_card", LiveCardFactory(this))
}
```

## iOS

Add `AtunwaViewFactories.swift` to the **Runner** target:

```swift
// ios/Runner/AtunwaViewFactories.swift
import Flutter
import MediaSDK
import UIKit

private final class WrappedView: NSObject, FlutterPlatformView {
    private let wrapped: UIView
    init(_ view: UIView) { wrapped = view }
    func view() -> UIView { wrapped }
}

final class MiniPlayerFactory: NSObject, FlutterPlatformViewFactory {
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        MainActor.assumeIsolated {
            let view = SDKMiniPlayerView()
            view.onExpandListener = { MediaSDK.shared.launchPlayer() }
            view.onCloseClickListener = { MediaSDK.shared.reset() }
            return WrappedView(view)
        }
    }
}

final class LiveCardFactory: NSObject, FlutterPlatformViewFactory {
    func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        MainActor.assumeIsolated {
            let view = SDKLiveCardView()
            view.previewPlayer = MediaSDK.shared.publisherConfig?.players.first
            view.onCardClickListener = { [weak view] in
                let sdk = MediaSDK.shared
                if sdk.playerConfig != nil {
                    sdk.launchPlayer()
                } else if let preview = view?.previewPlayer {
                    sdk.loadPlayer(preview)
                }
            }
            return WrappedView(view)
        }
    }
}
```

Register them where you register the bridge:

```swift
let registrar = registrar(forPlugin: "AtunwaMediaBridge")!
AtunwaMediaBridge.register(with: registrar)
registrar.register(MiniPlayerFactory(), withId: "atunwa/mini_player")
registrar.register(LiveCardFactory(), withId: "atunwa/live_card")
```

## Dart

Add a widget for each view to `lib/atunwa_media.dart`:

```dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

Widget _platformView(String viewType) => switch (defaultTargetPlatform) {
      TargetPlatform.android => AndroidView(viewType: viewType),
      TargetPlatform.iOS => UiKitView(viewType: viewType),
      _ => const SizedBox.shrink(),
    };

/// The SDK's "now playing" card. Always 16:9.
class AtunwaLiveCard extends StatelessWidget {
  const AtunwaLiveCard({super.key});

  @override
  Widget build(BuildContext context) =>
      AspectRatio(aspectRatio: 16 / 9, child: _platformView('atunwa/live_card'));
}

/// The SDK's mini player bar. Show it while something is playing.
class AtunwaMiniPlayer extends StatelessWidget {
  const AtunwaMiniPlayer({super.key});

  @override
  Widget build(BuildContext context) =>
      SizedBox(height: 64, child: _platformView('atunwa/mini_player'));
}
```

Show the mini player only while a station is active, using the state stream from the bridge:

```dart
Scaffold(
  body: Column(
    children: [
      const AtunwaLiveCard(),
      const Expanded(child: StationList()),
    ],
  ),
  bottomNavigationBar: StreamBuilder<String>(
    stream: AtunwaMedia.state,
    builder: (context, snapshot) {
      final state = snapshot.data ?? 'idle';
      return state == 'idle' ? const SizedBox.shrink() : const AtunwaMiniPlayer();
    },
  ),
)
```

The native apps also hide the mini player while the full-screen player is open, using
`isDedicatedPlayerActive`. Because the full-screen player covers your Flutter screen, you don't
usually need that in Flutter. If you do, add it to the bridge as a second event channel.

---

**Next:** [Troubleshooting](troubleshooting.md)
