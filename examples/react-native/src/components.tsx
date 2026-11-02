import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Image,
  Pressable,
  StyleSheet,
  Text,
  useColorScheme,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  AtunwaMiniPlayer,
  type AtunwaPlayback,
  type AtunwaStation,
  showsMiniPlayer,
} from './atunwaMedia';

export const brand = '#0057FF'; // the SDK's default primary color

export function useColors() {
  const dark = useColorScheme() === 'dark';
  return {
    background: dark ? '#000000' : '#FFFFFF',
    surface: dark ? '#1C1C1E' : '#F2F2F7',
    text: dark ? '#FFFFFF' : '#000000',
    secondaryText: dark ? '#AEAEB2' : '#6C6C70',
    primary: dark ? '#8AB0FF' : brand,
  };
}

/** "TV" for video stations, "Radio" otherwise. */
export const kindLabel = (station: AtunwaStation) =>
  station.isVideo ? 'TV' : 'Radio';

/** A screen with a title bar and the SDK's mini player pinned to the bottom. */
export function Screen({
  title,
  logo,
  playback,
  children,
}: {
  title: string;
  logo?: string | null;
  playback: AtunwaPlayback;
  children: ReactNode;
}) {
  const insets = useSafeAreaInsets();
  const colors = useColors();
  const mini = showsMiniPlayer(playback);
  return (
    <View
      style={[
        styles.flex,
        { backgroundColor: colors.background, paddingTop: insets.top },
      ]}
    >
      <View style={styles.titleBar}>
        {logo ? <StationLogo url={logo} size={32} /> : null}
        <Text style={[styles.title, { color: colors.text }]} numberOfLines={1}>
          {title}
        </Text>
      </View>
      <View style={styles.flex}>{children}</View>
      {mini ? (
        <View style={{ paddingBottom: insets.bottom }}>
          <AtunwaMiniPlayer />
        </View>
      ) : null}
    </View>
  );
}

export function StationLogo({
  url,
  size,
}: {
  url?: string | null;
  size: number;
}) {
  const colors = useColors();
  const style = {
    width: size,
    height: size,
    borderRadius: size / 5,
    backgroundColor: colors.surface,
  };
  return url ? (
    <Image source={{ uri: url }} style={style} resizeMode="contain" />
  ) : (
    <View style={style} />
  );
}

export function Button({
  title,
  onPress,
  outlined,
}: {
  title: string;
  onPress: () => void;
  outlined?: boolean;
}) {
  const colors = useColors();
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        outlined
          ? { borderWidth: 1, borderColor: colors.primary }
          : { backgroundColor: colors.primary },
        pressed && styles.pressed,
      ]}
    >
      <Text
        style={[
          styles.buttonText,
          { color: outlined ? colors.primary : '#FFFFFF' },
        ]}
      >
        {title}
      </Text>
    </Pressable>
  );
}

/**
 * A station in a list. Tapping the row opens the full-screen player; the trailing button plays or
 * pauses in place.
 */
export function StationRow({
  station,
  isActive,
  isPlaying,
  onOpen,
  onPlayPause,
}: {
  station: AtunwaStation;
  isActive: boolean;
  isPlaying: boolean;
  onOpen: () => void;
  onPlayPause: () => void;
}) {
  const colors = useColors();
  const showPause = isActive && isPlaying;
  return (
    <Pressable
      onPress={onOpen}
      style={({ pressed }) => [
        styles.row,
        isActive && { backgroundColor: colors.surface },
        pressed && styles.pressed,
      ]}
    >
      <StationLogo url={station.logo} size={48} />
      <View style={styles.flex}>
        <Text
          style={[
            styles.rowTitle,
            { color: colors.text },
            isActive && styles.bold,
          ]}
          numberOfLines={1}
        >
          {station.name}
        </Text>
        <Text
          style={{ color: isActive ? colors.primary : colors.secondaryText }}
        >
          {isActive
            ? `${kindLabel(station)} · Now playing`
            : kindLabel(station)}
        </Text>
      </View>
      <Pressable
        onPress={onPlayPause}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel={showPause ? 'Pause' : 'Play'}
        style={[styles.playButton, { backgroundColor: colors.primary }]}
      >
        <Text style={styles.playIcon}>{showPause ? '❚❚' : '▶'}</Text>
      </Pressable>
    </Pressable>
  );
}

export function Loading() {
  return (
    <View style={styles.center}>
      <ActivityIndicator size="large" />
    </View>
  );
}

/** A centered message with an optional action, for errors and empty lists. */
export function Message({
  title,
  message,
  actionTitle,
  onAction,
}: {
  title: string;
  message: string;
  actionTitle?: string;
  onAction?: () => void;
}) {
  const colors = useColors();
  return (
    <View style={styles.center}>
      <Text style={[styles.messageTitle, { color: colors.text }]}>{title}</Text>
      <Text style={[styles.messageText, { color: colors.secondaryText }]}>
        {message}
      </Text>
      {actionTitle && onAction ? (
        <Button title={actionTitle} onPress={onAction} />
      ) : null}
    </View>
  );
}

/** Shown when the build has no Atunwa key. */
export function MissingKeyScreen({ problem }: { problem: string }) {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  return (
    <View
      style={[
        styles.flex,
        styles.missing,
        { backgroundColor: colors.background, paddingTop: insets.top },
      ]}
    >
      <Text style={[styles.title, { color: colors.text }]}>
        Add your Atunwa key
      </Text>
      <Text style={{ color: colors.text }}>{problem}</Text>
      <Text style={{ color: colors.secondaryText }}>
        Set the mode and the key Atunwa gave you in atunwa.config.json, then
        reload the app:
      </Text>
      <Text
        style={[
          styles.code,
          { backgroundColor: colors.surface, color: colors.text },
        ]}
      >
        {
          '{\n  "ATUNWA_APP_MODE": "single",\n  "ATUNWA_PLAYER_KEY": "…"\n}\n\nor\n\n{\n  "ATUNWA_APP_MODE": "publisher",\n  "ATUNWA_PUBLISHER_SDK_KEY": "…"\n}'
        }
      </Text>
    </View>
  );
}

export const styles = StyleSheet.create({
  flex: { flex: 1 },
  titleBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  title: { fontSize: 20, fontWeight: '600', flexShrink: 1 },
  button: { paddingHorizontal: 20, paddingVertical: 12, borderRadius: 24 },
  buttonText: { fontSize: 16, fontWeight: '600' },
  pressed: { opacity: 0.6 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  rowTitle: { fontSize: 16, fontWeight: '500' },
  bold: { fontWeight: '700' },
  playButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  playIcon: { color: '#FFFFFF', fontSize: 14 },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
    gap: 12,
  },
  messageTitle: { fontSize: 17, fontWeight: '600', textAlign: 'center' },
  messageText: { fontSize: 15, textAlign: 'center' },
  missing: { padding: 24, gap: 12, justifyContent: 'center' },
  code: {
    fontFamily: 'Menlo',
    fontSize: 13,
    padding: 12,
    borderRadius: 8,
    overflow: 'hidden',
  },
});
