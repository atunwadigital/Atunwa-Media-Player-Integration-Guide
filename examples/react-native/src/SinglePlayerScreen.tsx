import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  AtunwaLiveCard,
  AtunwaMedia,
  type AtunwaStation,
  hasStream,
  usePlayback,
} from './atunwaMedia';
import {
  Button,
  kindLabel,
  Loading,
  Message,
  Screen,
  StationLogo,
  useColors,
} from './components';

/**
 * Single-player mode: an app for **one station**, identified by its player API key.
 *
 * 1. `initializePlayer` fetches the station's config and makes it the active station, so its name
 *    and logo are available before anything plays.
 * 2. `load` starts its stream, and `openPlayer` opens the SDK's full-screen player.
 *
 * If you don't need the station's details before playback starts, one call does both steps:
 * `AtunwaMedia.launchPlayer(playerApiKey)`.
 */
export function SinglePlayerScreen({ playerApiKey }: { playerApiKey: string }) {
  const playback = usePlayback();
  const colors = useColors();
  const [station, setStation] = useState<AtunwaStation | null>(null);
  const [error, setError] = useState<string | null>(null);

  const initialize = useCallback(async () => {
    setError(null);
    try {
      // Already active (after a reload, say): initializing again would interrupt playback.
      const active = await AtunwaMedia.activeStation();
      setStation(
        active?.assetKey === playerApiKey
          ? active
          : await AtunwaMedia.initializePlayer(playerApiKey),
      );
    } catch (e) {
      setError(`Check the player API key and your connection. (${String(e)})`);
    }
  }, [playerApiKey]);

  useEffect(() => {
    initialize();
  }, [initialize]);

  /**
   * Starts the station's stream and optionally opens the full-screen player. The player plays any
   * preroll the station has before the stream.
   */
  const start = async (openPlayer: boolean) => {
    // The mini player's close button calls reset(), which clears the active station. Initialize
    // again (from the SDK's cache, so it's instant) before playing.
    if (!(await AtunwaMedia.activeStation())) {
      await AtunwaMedia.initializePlayer(playerApiKey);
    }
    await AtunwaMedia.load();
    if (openPlayer) {
      await AtunwaMedia.openPlayer();
    }
  };

  let content;
  if (!station && error) {
    content = (
      <Message
        title="Couldn't load the station"
        message={error}
        actionTitle="Try again"
        onAction={initialize}
      />
    );
  } else if (!station) {
    content = <Loading />;
  } else {
    content = (
      <ScrollView contentContainerStyle={styles.content}>
        {hasStream(playback) ? (
          // Once the stream is loaded, the SDK's live card plays and pauses in place.
          <AtunwaLiveCard />
        ) : (
          <View style={[styles.hero, { backgroundColor: colors.surface }]}>
            <StationLogo url={station.logo} size={96} />
            <Text style={[styles.name, { color: colors.text }]}>
              {station.name}
            </Text>
            <Text style={{ color: colors.secondaryText }}>
              {[kindLabel(station), station.publisherName]
                .filter(Boolean)
                .join(' · ')}
            </Text>
            <View style={styles.buttons}>
              <Button
                title={station.isVideo ? 'Watch live' : 'Listen live'}
                onPress={() => start(true)}
              />
              <Button title="Play here" outlined onPress={() => start(false)} />
            </View>
          </View>
        )}
        <Text style={[styles.note, { color: colors.secondaryText }]}>
          This build uses a player API key, so it plays this one station. To
          offer all of a publisher's stations, build with a publisher SDK key
          instead.
        </Text>
      </ScrollView>
    );
  }

  return (
    <Screen title={station?.name ?? 'Atunwa'} playback={playback}>
      {content}
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 16 },
  hero: { alignItems: 'center', padding: 24, borderRadius: 16, gap: 6 },
  name: { fontSize: 22, fontWeight: '700', marginTop: 10, textAlign: 'center' },
  buttons: { flexDirection: 'row', gap: 12, marginTop: 14 },
  note: { fontSize: 13 },
});
