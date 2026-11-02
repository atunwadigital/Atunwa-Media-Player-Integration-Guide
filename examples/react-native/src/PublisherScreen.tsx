import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  AtunwaLiveCard,
  AtunwaMedia,
  type AtunwaPublisher,
  isPlaying,
  usePlayback,
} from './atunwaMedia';
import { Loading, Message, Screen, StationRow, useColors } from './components';

type Filter = 'All' | 'Radio' | 'TV';

/**
 * Publisher mode: an app offering **every station** a publisher has, identified by the
 * publisher's SDK key.
 *
 * 1. `initializePublisher` fetches the publisher and its stations. It doesn't start anything.
 * 2. `loadStation` switches to a station and opens the SDK's full-screen player.
 *    `activateStation` switches to it and plays in place.
 *
 * The full-screen player also shows a rail of the publisher's other TV stations.
 */
export function PublisherScreen({
  publisherSdkKey,
}: {
  publisherSdkKey: string;
}) {
  const playback = usePlayback();
  const colors = useColors();
  const [publisher, setPublisher] = useState<AtunwaPublisher | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<Filter>('All');

  /** Uses the SDK's memory or disk cache when it has one, so the app also starts offline. */
  const initialize = useCallback(async () => {
    setError(null);
    try {
      setPublisher(await AtunwaMedia.initializePublisher(publisherSdkKey));
    } catch (e) {
      setError(
        `Check the publisher SDK key and your connection. (${String(e)})`,
      );
    }
  }, [publisherSdkKey]);

  useEffect(() => {
    initialize();
  }, [initialize]);

  /** Always goes to the network, so a failure is reported rather than hidden by the cache. */
  const refresh = async () => {
    setRefreshing(true);
    try {
      setPublisher(await AtunwaMedia.refreshPublisher(publisherSdkKey));
    } catch {
      Alert.alert("Couldn't refresh the stations.");
    }
    setRefreshing(false);
  };

  /**
   * Runs `action`, first initializing again if the mini player's close button has reset the SDK,
   * which also clears the publisher config the station is looked up in.
   */
  const withPublisher = async (action: () => Promise<void>) => {
    try {
      await action();
    } catch (e) {
      if ((e as { code?: string }).code !== 'unknownStation') {
        throw e;
      }
      await AtunwaMedia.initializePublisher(publisherSdkKey);
      await action();
    }
  };

  let content;
  if (!publisher && error) {
    content = (
      <Message
        title="Couldn't load the stations"
        message={error}
        actionTitle="Try again"
        onAction={initialize}
      />
    );
  } else if (!publisher) {
    content = <Loading />;
  } else if (publisher.stations.length === 0) {
    content = (
      <Message
        title="No stations yet"
        message="This publisher has no active stations on the Atunwa dashboard."
      />
    );
  } else {
    const all = publisher.stations;
    const showFilter = all.some(s => s.isVideo) && all.some(s => !s.isVideo);
    const stations = all.filter(
      s => filter === 'All' || (filter === 'TV') === s.isVideo,
    );
    const playing = isPlaying(playback);

    content = (
      <FlatList
        data={stations}
        keyExtractor={s => s.assetKey}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={refresh} />
        }
        ListHeaderComponent={
          <View>
            {/* Shows the active station, or the publisher's first station until one is picked.
                Its own play button starts that station in place; a tap opens the player. */}
            <View style={styles.card}>
              <AtunwaLiveCard />
            </View>
            <View style={styles.header}>
              <Text style={[styles.count, { color: colors.text }]}>
                {all.length} stations
              </Text>
              {showFilter
                ? (['All', 'Radio', 'TV'] as const).map(f => (
                    <Pressable
                      key={f}
                      onPress={() => setFilter(f)}
                      style={[
                        styles.chip,
                        { borderColor: colors.primary },
                        filter === f && { backgroundColor: colors.primary },
                      ]}
                    >
                      <Text
                        style={{
                          color: filter === f ? '#FFFFFF' : colors.primary,
                        }}
                      >
                        {f}
                      </Text>
                    </Pressable>
                  ))
                : null}
            </View>
          </View>
        }
        renderItem={({ item }) => {
          const isActive = item.assetKey === playback.activeAssetKey;
          return (
            <StationRow
              station={item}
              isActive={isActive}
              isPlaying={playing}
              onOpen={() =>
                withPublisher(() => AtunwaMedia.loadStation(item.assetKey))
              }
              onPlayPause={() => {
                if (!isActive) {
                  withPublisher(() =>
                    AtunwaMedia.activateStation(item.assetKey),
                  );
                } else if (playing) {
                  AtunwaMedia.pause();
                } else {
                  AtunwaMedia.play();
                }
              }}
            />
          );
        }}
      />
    );
  }

  return (
    <Screen
      title={publisher?.name ?? 'Atunwa'}
      logo={publisher?.logo}
      playback={playback}
    >
      {content}
    </Screen>
  );
}

const styles = StyleSheet.create({
  card: { padding: 16 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 4,
  },
  count: { flex: 1, fontSize: 17, fontWeight: '600' },
  chip: {
    borderWidth: 1,
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
});
