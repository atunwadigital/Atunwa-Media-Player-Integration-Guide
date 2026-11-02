import 'package:flutter/material.dart';

import 'atunwa_media.dart';

class StationLogo extends StatelessWidget {
  const StationLogo({super.key, required this.url, required this.size});

  final String? url;
  final double size;

  @override
  Widget build(BuildContext context) {
    final placeholder = ColoredBox(color: Theme.of(context).colorScheme.surfaceContainerHighest);
    return ClipRRect(
      borderRadius: BorderRadius.circular(size / 5),
      child: SizedBox.square(
        dimension: size,
        child: url == null || url!.isEmpty
            ? placeholder
            : Image.network(url!, fit: BoxFit.contain, errorBuilder: (_, _, _) => placeholder),
      ),
    );
  }
}

/// A station in a list. Tapping the row opens the full-screen player; the trailing button plays or
/// pauses in place.
class StationTile extends StatelessWidget {
  const StationTile({
    super.key,
    required this.station,
    required this.isActive,
    required this.isPlaying,
    required this.onOpen,
    required this.onPlayPause,
  });

  final AtunwaStation station;
  final bool isActive;
  final bool isPlaying;
  final VoidCallback onOpen;
  final VoidCallback onPlayPause;

  @override
  Widget build(BuildContext context) {
    final showPause = isActive && isPlaying;
    return ListTile(
      selected: isActive,
      leading: StationLogo(url: station.logo, size: 48),
      title: Text(station.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(isActive ? '${station.kindLabel} · Now playing' : station.kindLabel),
      trailing: IconButton(
        icon: Icon(showPause ? Icons.pause_circle_filled : Icons.play_circle_filled, size: 32),
        tooltip: showPause ? 'Pause' : 'Play',
        onPressed: onPlayPause,
      ),
      onTap: onOpen,
    );
  }
}

/// A centered message with an optional action, for errors and empty lists.
class MessageView extends StatelessWidget {
  const MessageView({super.key, required this.title, required this.message, this.actionLabel, this.onAction});

  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(title, style: text.titleMedium, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, style: text.bodyMedium, textAlign: TextAlign.center),
            if (actionLabel != null) ...[
              const SizedBox(height: 16),
              FilledButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Shown when the build has no Atunwa key.
class MissingKeyScreen extends StatelessWidget {
  const MissingKeyScreen({super.key, required this.problem});

  final String problem;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Add your Atunwa key', style: theme.textTheme.headlineSmall),
              const SizedBox(height: 12),
              Text(problem),
              const SizedBox(height: 12),
              const Text('Copy atunwa.example.json to atunwa.json, set the mode and the key '
                  'Atunwa gave you, then run:'),
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Text(
                  'flutter run --dart-define-from-file=atunwa.json',
                  style: TextStyle(fontFamily: 'monospace'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
