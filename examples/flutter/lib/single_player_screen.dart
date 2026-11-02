import 'package:flutter/material.dart';

import 'atunwa_media.dart';
import 'widgets.dart';

/// Single-player mode: an app for **one station**, identified by its player API key.
///
/// 1. [AtunwaMedia.initialize] fetches the station's config and makes it the active station, so
///    its name and logo are available before anything plays.
/// 2. [AtunwaMedia.load] starts its stream, and [AtunwaMedia.openPlayer] opens the SDK's
///    full-screen player.
///
/// If you don't need the station's details before playback starts, one call does both steps:
/// `AtunwaMedia.launchPlayer(playerApiKey)`.
class SinglePlayerScreen extends StatefulWidget {
  const SinglePlayerScreen({super.key, required this.playerApiKey});

  final String playerApiKey;

  @override
  State<SinglePlayerScreen> createState() => _SinglePlayerScreenState();
}

class _SinglePlayerScreenState extends State<SinglePlayerScreen> {
  AtunwaStation? _station;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    setState(() => _error = null);
    try {
      // Already active (after a hot restart, say): initializing again would interrupt playback.
      final active = await AtunwaMedia.activeStation();
      final station = active?.assetKey == widget.playerApiKey
          ? active!
          : await AtunwaMedia.initialize(widget.playerApiKey);
      if (mounted) setState(() => _station = station);
    } catch (e) {
      if (mounted) setState(() => _error = 'Check the player API key and your connection. ($e)');
    }
  }

  /// Starts the station's stream and optionally opens the full-screen player. The player plays any
  /// preroll the station has before the stream.
  Future<void> _start({required bool openPlayer}) async {
    // The mini player's close button calls reset(), which clears the active station. Initialize
    // again (from the SDK's cache, so it's instant) before playing.
    if (await AtunwaMedia.activeStation() == null) {
      await AtunwaMedia.initialize(widget.playerApiKey);
    }
    await AtunwaMedia.load();
    if (openPlayer) await AtunwaMedia.openPlayer();
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<AtunwaPlayback>(
      stream: AtunwaMedia.playback,
      initialData: const AtunwaPlayback(),
      builder: (context, snapshot) {
        final playback = snapshot.data!;
        return Scaffold(
          appBar: AppBar(title: Text(_station?.name ?? 'Atunwa')),
          bottomNavigationBar: playback.showsMiniPlayer ? const AtunwaMiniPlayer() : null,
          body: _body(playback),
        );
      },
    );
  }

  Widget _body(AtunwaPlayback playback) {
    final station = _station;
    if (station == null && _error != null) {
      return MessageView(
        title: "Couldn't load the station",
        message: _error!,
        actionLabel: 'Try again',
        onAction: _initialize,
      );
    }
    if (station == null) return const Center(child: CircularProgressIndicator());

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (playback.hasStream)
          // Once the stream is loaded, the SDK's live card plays and pauses in place.
          const AtunwaLiveCard()
        else
          _StationHero(
            station: station,
            onListen: () => _start(openPlayer: true),
            onPlayHere: () => _start(openPlayer: false),
          ),
        const SizedBox(height: 16),
        Text(
          "This build uses a player API key, so it plays this one station. To offer all of a "
          "publisher's stations, build with a publisher SDK key instead.",
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }
}

/// The station's branding and play buttons, shown before its stream is loaded.
class _StationHero extends StatelessWidget {
  const _StationHero({required this.station, required this.onListen, required this.onPlayHere});

  final AtunwaStation station;
  final VoidCallback onListen;
  final VoidCallback onPlayHere;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            StationLogo(url: station.logo, size: 96),
            const SizedBox(height: 16),
            Text(station.name, style: text.titleLarge, textAlign: TextAlign.center),
            Text(
              [station.kindLabel, ?station.publisherName].join(' · '),
              style: text.bodyMedium,
            ),
            const SizedBox(height: 20),
            Wrap(
              spacing: 12,
              children: [
                FilledButton(
                  onPressed: onListen,
                  child: Text(station.isVideo ? 'Watch live' : 'Listen live'),
                ),
                OutlinedButton(onPressed: onPlayHere, child: const Text('Play here')),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
