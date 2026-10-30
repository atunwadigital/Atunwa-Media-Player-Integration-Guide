# Embedding native views in React Native

The SDK's live card and mini player are native views. You can place them inside your React Native
screens as native UI components: each platform registers a view manager under a name, and
JavaScript renders it with `requireNativeComponent`.

This guide assumes you've set up the module from [Getting started](getting-started.md). Like the
module, these view managers use the classic API, which the New Architecture loads through its
interop layer.

- [Which views to embed](#which-views-to-embed)
- [Android](#android)
- [iOS](#ios)
- [JavaScript](#javascript)

---

## Which views to embed

| View | Embed it? | Notes |
|---|---|---|
| `SDKMiniPlayerView` | Yes | A fixed-height bar. Its callbacks are handled natively below |
| `SDKLiveCardView` | Yes | Always 16:9. Give it `aspectRatio: 16 / 9` |
| Full-screen player | No | Use `AtunwaMedia.openPlayer()`: it's a native screen of its own |
| Banner and companion ad views | Usually no | The live card and the full-screen player already show the station's ads |

Both views observe the SDK on their own, so once they're on screen you don't pass any props. See
UI components ([iOS](../../ios/docs/ui-components.md),
[Android](../../android/docs/ui-components.md)) for what each one shows.

## Android

React Native lays out native views itself and doesn't re-run Android layout when a view changes its
own children, which the SDK's views do as ads and video come and go. Wrapping each view in a frame
that lays itself out again fixes that.

Create `android/app/src/main/java/com/example/myapp/atunwa/AtunwaViewManagers.kt`:

```kotlin
package com.example.myapp.atunwa

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.atunwadigital.mediasdk.MediaSDK
import com.atunwadigital.mediasdk.ui.SDKLiveCardView
import com.atunwadigital.mediasdk.ui.SDKMiniPlayerView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

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
```

Return the managers from `AtunwaMediaPackage`:

```kotlin
override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
    listOf(MiniPlayerManager(), LiveCardManager())
```

## iOS

Add `AtunwaViewManagers.swift` and `AtunwaViewManagers.m` to the **MyApp** target. The bridging
header from [Getting started](getting-started.md#5-the-ios-module) already imports
`RCTViewManager.h`.

```objc
// ios/MyApp/AtunwaViewManagers.m
#import <React/RCTViewManager.h>

@interface RCT_EXTERN_MODULE(AtunwaMiniPlayerManager, RCTViewManager)
@end

@interface RCT_EXTERN_MODULE(AtunwaLiveCardManager, RCTViewManager)
@end
```

```swift
// ios/MyApp/AtunwaViewManagers.swift
import MediaSDK
import UIKit

@objc(AtunwaMiniPlayerManager)
final class AtunwaMiniPlayerManager: RCTViewManager {
    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
        MainActor.assumeIsolated {
            let view = SDKMiniPlayerView()
            view.onExpandListener = { MediaSDK.shared.launchPlayer() }
            view.onCloseClickListener = { MediaSDK.shared.reset() }
            return view
        }
    }
}

@objc(AtunwaLiveCardManager)
final class AtunwaLiveCardManager: RCTViewManager {
    override static func requiresMainQueueSetup() -> Bool { true }

    override func view() -> UIView! {
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
            return view
        }
    }
}
```

React Native strips the `Manager` suffix, so these register as `AtunwaMiniPlayer` and
`AtunwaLiveCard`, the same names as on Android.

## JavaScript

Add the components to `src/atunwaMedia.ts`:

```tsx
import { requireNativeComponent, type ViewProps } from 'react-native';

const NativeLiveCard = requireNativeComponent<ViewProps>('AtunwaLiveCard');
const NativeMiniPlayer = requireNativeComponent<ViewProps>('AtunwaMiniPlayer');

/** The SDK's "now playing" card. Always 16:9. */
export function AtunwaLiveCard({ style, ...props }: ViewProps) {
  return <NativeLiveCard style={[{ width: '100%', aspectRatio: 16 / 9 }, style]} {...props} />;
}

/** The SDK's mini player bar. Show it while something is playing. */
export function AtunwaMiniPlayer({ style, ...props }: ViewProps) {
  return <NativeMiniPlayer style={[{ width: '100%', height: 64 }, style]} {...props} />;
}
```

The file now contains JSX, so rename it to `atunwaMedia.tsx`.

Show the mini player only while a station is active:

```tsx
export function HomeScreen() {
  const state = useAtunwaState();

  return (
    <View style={{ flex: 1 }}>
      <AtunwaLiveCard />
      <StationList />
      {state !== 'idle' && <AtunwaMiniPlayer />}
    </View>
  );
}
```

The native apps also hide the mini player while the full-screen player is open, using
`isDedicatedPlayerActive`. Because the full-screen player covers your React Native screen, you
don't usually need that. If you do, add it to the module as a second event.

---

**Next:** [Expo](expo.md) · [Troubleshooting](troubleshooting.md)
