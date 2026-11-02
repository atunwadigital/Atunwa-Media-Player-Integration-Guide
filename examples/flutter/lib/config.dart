// The Atunwa key this build uses, from `--dart-define-from-file=atunwa.json`. Atunwa issues both
// kinds of key, and both are safe to ship in an app. See atunwa.example.json.

sealed class AtunwaConfig {
  const AtunwaConfig();

  static AtunwaConfig fromEnvironment() {
    const mode = String.fromEnvironment('ATUNWA_APP_MODE', defaultValue: 'single');
    const playerKey = String.fromEnvironment('ATUNWA_PLAYER_KEY');
    const publisherKey = String.fromEnvironment('ATUNWA_PUBLISHER_SDK_KEY');

    return switch (mode.toLowerCase()) {
      'single' => playerKey.isEmpty
          ? const MissingKey('ATUNWA_APP_MODE is single, but ATUNWA_PLAYER_KEY is empty.')
          : const SinglePlayer(playerKey),
      'publisher' => publisherKey.isEmpty
          ? const MissingKey('ATUNWA_APP_MODE is publisher, but ATUNWA_PUBLISHER_SDK_KEY is empty.')
          : const Publisher(publisherKey),
      _ => MissingKey('ATUNWA_APP_MODE must be single or publisher, not "$mode".'),
    };
  }
}

/// One station, identified by its player API key.
class SinglePlayer extends AtunwaConfig {
  const SinglePlayer(this.playerApiKey);
  final String playerApiKey;
}

/// Every station a publisher has, identified by the publisher's SDK key.
class Publisher extends AtunwaConfig {
  const Publisher(this.publisherSdkKey);
  final String publisherSdkKey;
}

/// The build has no usable key. [problem] says what to fix.
class MissingKey extends AtunwaConfig {
  const MissingKey(this.problem);
  final String problem;
}
