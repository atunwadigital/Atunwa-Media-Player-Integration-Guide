// The Dart half of the Atunwa Media SDK bridge, from flutter/docs/getting-started.md and
// flutter/docs/native-views.md, extended with what this example needs:
//
// - initialize() returns the station, and activeStation() reads the active one.
// - load() starts the active station's stream.
// - refreshPublisher(), and initializePublisher() returns the publisher as well as its stations.
// - playback reports the active station and whether the full-screen player is open, not just
//   the state.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A station: one of a publisher's, or the single station a player API key identifies.
class AtunwaStation {
  const AtunwaStation({
    required this.assetKey,
    required this.name,
    required this.logo,
    required this.isVideo,
    this.publisherName,
  });

  factory AtunwaStation.fromMap(Map<Object?, Object?> map) => AtunwaStation(
        assetKey: map['assetKey'] as String,
        name: map['name'] as String,
        logo: map['logo'] as String? ?? '',
        isVideo: map['isVideo'] as bool? ?? false,
        publisherName: map['publisherName'] as String?,
      );

  final String assetKey;
  final String name;
  final String logo;
  final bool isVideo;
  final String? publisherName;

  /// "TV" for video stations, "Radio" otherwise.
  String get kindLabel => isVideo ? 'TV' : 'Radio';
}

/// A publisher and its stations.
class AtunwaPublisher {
  const AtunwaPublisher({required this.name, required this.logo, required this.stations});

  factory AtunwaPublisher.fromMap(Map<Object?, Object?> map) => AtunwaPublisher(
        name: map['name'] as String,
        logo: map['logo'] as String?,
        stations: (map['stations'] as List<Object?>)
            .map((s) => AtunwaStation.fromMap(s as Map<Object?, Object?>))
            .toList(),
      );

  final String name;
  final String? logo;
  final List<AtunwaStation> stations;
}

/// A snapshot of the SDK's playback.
class AtunwaPlayback {
  const AtunwaPlayback({
    this.state = 'idle',
    this.playWhenReady = false,
    this.activeAssetKey,
    this.isPlayerOpen = false,
  });

  factory AtunwaPlayback.fromMap(Map<Object?, Object?> map) => AtunwaPlayback(
        state: map['state'] as String,
        playWhenReady: map['playWhenReady'] as bool,
        activeAssetKey: map['activeAssetKey'] as String?,
        isPlayerOpen: map['isPlayerOpen'] as bool,
      );

  /// idle, initializing, loading, ready, playing, paused, buffering, error or adPlaying.
  final String state;
  final bool playWhenReady;

  /// The asset key of the active station, if any.
  final String? activeAssetKey;

  /// The SDK's full-screen player is open, including while it's in Picture-in-Picture.
  final bool isPlayerOpen;

  /// A stream is loaded: playing, paused, buffering, in an ad, or failed.
  bool get hasStream => state != 'idle' && state != 'initializing';

  bool get isPlaying =>
      playWhenReady && const {'loading', 'buffering', 'playing', 'adPlaying'}.contains(state);

  /// Show the mini player while a stream is loaded and the full-screen player isn't open.
  bool get showsMiniPlayer => hasStream && !isPlayerOpen;
}

/// Calls the native Atunwa Media SDK through the app's platform channels.
class AtunwaMedia {
  static const _methods = MethodChannel('atunwa/media');
  static const _events = EventChannel('atunwa/media/state');

  /// Playback updates. The native side sends the current value as soon as you listen.
  static final Stream<AtunwaPlayback> playback = _events
      .receiveBroadcastStream()
      .map((event) => AtunwaPlayback.fromMap(event as Map<Object?, Object?>));

  /// Call once at startup, before anything else.
  static Future<void> setup() => _methods.invokeMethod('setup');

  // Single-player mode

  /// Fetches the station and makes it the active station. Doesn't start its stream.
  static Future<AtunwaStation> initialize(String apiKey) async {
    final map = await _methods.invokeMapMethod<Object?, Object?>('initialize', {'apiKey': apiKey});
    return AtunwaStation.fromMap(map!);
  }

  /// The active station, or null when there isn't one (for example after [reset]).
  static Future<AtunwaStation?> activeStation() async {
    final map = await _methods.invokeMapMethod<Object?, Object?>('activeStation');
    return map == null ? null : AtunwaStation.fromMap(map);
  }

  /// Starts the active station's stream, unless it's already loaded.
  static Future<void> load() => _methods.invokeMethod('load');

  /// Initializes [apiKey], starts it and opens the full-screen player, in one call.
  static Future<void> launchPlayer(String apiKey) =>
      _methods.invokeMethod('launchPlayer', {'apiKey': apiKey});

  /// Opens the full-screen player for whatever is active.
  static Future<void> openPlayer() => _methods.invokeMethod('openPlayer');

  // Publisher mode

  /// Fetches the publisher and its stations, from the SDK's cache when it has them.
  static Future<AtunwaPublisher> initializePublisher(String sdkKey) async {
    final map = await _methods
        .invokeMapMethod<Object?, Object?>('initializePublisher', {'sdkKey': sdkKey});
    return AtunwaPublisher.fromMap(map!);
  }

  /// Fetches the publisher and its stations from the network, never from the cache.
  static Future<AtunwaPublisher> refreshPublisher(String sdkKey) async {
    final map =
        await _methods.invokeMapMethod<Object?, Object?>('refreshPublisher', {'sdkKey': sdkKey});
    return AtunwaPublisher.fromMap(map!);
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

  /// Stops playback and clears the active station and the publisher config.
  static Future<void> reset() => _methods.invokeMethod('reset');
}

// Native views

Widget _platformView(String viewType) => switch (defaultTargetPlatform) {
      TargetPlatform.android => AndroidView(viewType: viewType),
      TargetPlatform.iOS => UiKitView(viewType: viewType),
      _ => const SizedBox.shrink(),
    };

/// The SDK's "now playing" card. Always 16:9.
///
/// It shows the active station or, before one is active, the publisher's first station, which
/// its play button starts in place. Create it after `initialize` or `initializePublisher`.
class AtunwaLiveCard extends StatelessWidget {
  const AtunwaLiveCard({super.key});

  @override
  Widget build(BuildContext context) =>
      AspectRatio(aspectRatio: 16 / 9, child: _platformView('atunwa/live_card'));
}

/// The SDK's mini player bar. Its close button calls `reset()`.
///
/// The bar is 62 points tall on iOS. On Android it's taller, because it includes margins for its
/// shadow.
class AtunwaMiniPlayer extends StatelessWidget {
  const AtunwaMiniPlayer({super.key});

  @override
  Widget build(BuildContext context) => SafeArea(
        top: false,
        child: SizedBox(
          height: defaultTargetPlatform == TargetPlatform.iOS ? 62 : 76,
          child: _platformView('atunwa/mini_player'),
        ),
      );
}
