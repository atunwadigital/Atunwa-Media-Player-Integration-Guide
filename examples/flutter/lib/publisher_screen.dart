import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'atunwa_media.dart';
import 'widgets.dart';

enum _Filter { all, radio, tv }

/// Publisher mode: an app offering **every station** a publisher has, identified by the
/// publisher's SDK key.
///
/// 1. [AtunwaMedia.initializePublisher] fetches the publisher and its stations. It doesn't start
///    anything.
/// 2. [AtunwaMedia.loadStation] switches to a station and opens the SDK's full-screen player.
///    [AtunwaMedia.activateStation] switches to it and plays in place.
///
/// The full-screen player also shows a rail of the publisher's other TV stations.
class PublisherScreen extends StatefulWidget {
  const PublisherScreen({super.key, required this.publisherSdkKey});

  final String publisherSdkKey;

  @override
  State<PublisherScreen> createState() => _PublisherScreenState();
}

class _PublisherScreenState extends State<PublisherScreen> {
  AtunwaPublisher? _publisher;
  String? _error;
  _Filter _filter = _Filter.all;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  /// Uses the SDK's memory or disk cache when it has one, so the app also starts offline.
  Future<void> _initialize() async {
    setState(() => _error = null);
    try {
      final publisher = await AtunwaMedia.initializePublisher(widget.publisherSdkKey);
      if (mounted) setState(() => _publisher = publisher);
    } catch (e) {
      if (mounted) setState(() => _error = 'Check the publisher SDK key and your connection. ($e)');
    }
  }

  /// Always goes to the network, so a failure is reported rather than hidden by the cache.
  Future<void> _refresh() async {
    try {
      final publisher = await AtunwaMedia.refreshPublisher(widget.publisherSdkKey);
      if (mounted) setState(() => _publisher = publisher);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text("Couldn't refresh the stations.")));
      }
    }
  }

  /// Runs [action], first initializing again if the mini player's close button has reset the
  /// SDK, which also clears the publisher config the station is looked up in.
  Future<void> _withPublisher(Future<void> Function() action) async {
    try {
      await action();
    } on PlatformException catch (e) {
      if (e.code != 'unknownStation') rethrow;
      await AtunwaMedia.initializePublisher(widget.publisherSdkKey);
      await action();
    }
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<AtunwaPlayback>(
      stream: AtunwaMedia.playback,
      initialData: const AtunwaPlayback(),
      builder: (context, snapshot) {
        final playback = snapshot.data!;
        final publisher = _publisher;
        return Scaffold(
          appBar: AppBar(
            title: Row(
              children: [
                if (publisher?.logo case final logo?) ...[
                  StationLogo(url: logo, size: 32),
                  const SizedBox(width: 12),
                ],
                Text(publisher?.name ?? 'Atunwa'),
              ],
            ),
          ),
          bottomNavigationBar: playback.showsMiniPlayer ? const AtunwaMiniPlayer() : null,
          body: _body(publisher, playback),
        );
      },
    );
  }

  Widget _body(AtunwaPublisher? publisher, AtunwaPlayback playback) {
    if (publisher == null && _error != null) {
      return MessageView(
        title: "Couldn't load the stations",
        message: _error!,
        actionLabel: 'Try again',
        onAction: _initialize,
      );
    }
    if (publisher == null) return const Center(child: CircularProgressIndicator());
    if (publisher.stations.isEmpty) {
      return const MessageView(
        title: 'No stations yet',
        message: 'This publisher has no active stations on the Atunwa dashboard.',
      );
    }

    final all = publisher.stations;
    final hasTv = all.any((s) => s.isVideo);
    final hasRadio = all.any((s) => !s.isVideo);
    final stations = switch (_filter) {
      _Filter.all => all,
      _Filter.radio => all.where((s) => !s.isVideo).toList(),
      _Filter.tv => all.where((s) => s.isVideo).toList(),
    };

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        children: [
          // Shows the active station, or the publisher's first station until one is picked. Its
          // own play button starts that station in place; a tap on the card opens the player.
          const Padding(padding: EdgeInsets.all(16), child: AtunwaLiveCard()),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: Row(
              children: [
                Expanded(
                  child: Text('${all.length} stations', style: Theme.of(context).textTheme.titleMedium),
                ),
                if (hasTv && hasRadio)
                  SegmentedButton<_Filter>(
                    showSelectedIcon: false,
                    segments: const [
                      ButtonSegment(value: _Filter.all, label: Text('All')),
                      ButtonSegment(value: _Filter.radio, label: Text('Radio')),
                      ButtonSegment(value: _Filter.tv, label: Text('TV')),
                    ],
                    selected: {_filter},
                    onSelectionChanged: (s) => setState(() => _filter = s.first),
                  ),
              ],
            ),
          ),
          for (final station in stations)
            StationTile(
              station: station,
              isActive: station.assetKey == playback.activeAssetKey,
              isPlaying: playback.isPlaying,
              onOpen: () => _withPublisher(() => AtunwaMedia.loadStation(station.assetKey)),
              onPlayPause: () {
                if (station.assetKey != playback.activeAssetKey) {
                  _withPublisher(() => AtunwaMedia.activateStation(station.assetKey));
                } else if (playback.isPlaying) {
                  AtunwaMedia.pause();
                } else {
                  AtunwaMedia.play();
                }
              },
            ),
        ],
      ),
    );
  }
}
