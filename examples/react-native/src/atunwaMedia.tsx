// The JavaScript half of the Atunwa Media SDK module, from react-native/docs/getting-started.md
// and react-native/docs/native-views.md, extended with what this example needs:
//
// - initializePlayer() resolves with the station, and activeStation() reads the active one.
// - load() starts the active station's stream.
// - refreshPublisher(), and initializePublisher() resolves with the publisher as well as its
//   stations.
// - usePlayback() reports the active station and whether the full-screen player is open, not
//   just the state.
import { useEffect, useState } from 'react';
import {
  NativeEventEmitter,
  NativeModules,
  Platform,
  requireNativeComponent,
  StyleSheet,
  type ViewProps,
} from 'react-native';

const Native = NativeModules.AtunwaMedia;
const emitter = new NativeEventEmitter(Native);

export type AtunwaState =
  | 'idle'
  | 'initializing'
  | 'loading'
  | 'ready'
  | 'playing'
  | 'paused'
  | 'buffering'
  | 'error'
  | 'adPlaying';

export type AtunwaStation = {
  assetKey: string;
  name: string;
  logo: string;
  isVideo: boolean;
  publisherName?: string | null;
};

export type AtunwaPublisher = {
  name: string;
  logo?: string | null;
  stations: AtunwaStation[];
};

export type AtunwaPlayback = {
  state: AtunwaState;
  playWhenReady: boolean;
  /** The asset key of the active station, if any. */
  activeAssetKey?: string | null;
  /** The SDK's full-screen player is open, including while it's in Picture-in-Picture. */
  isPlayerOpen: boolean;
};

export const AtunwaMedia = {
  /** Call once at startup, before anything else. */
  setup: (): Promise<void> => Native.setup(),

  // Single-player mode
  /** Fetches the station and makes it the active station. Doesn't start its stream. */
  initializePlayer: (apiKey: string): Promise<AtunwaStation> =>
    Native.initializePlayer(apiKey),
  /** The active station, or null when there isn't one (for example after `reset`). */
  activeStation: (): Promise<AtunwaStation | null> => Native.activeStation(),
  /** Starts the active station's stream, unless it's already loaded. */
  load: (): Promise<void> => Native.load(),
  /** Initializes `apiKey`, starts it and opens the full-screen player, in one call. */
  launchPlayer: (apiKey: string): Promise<void> => Native.launchPlayer(apiKey),
  /** Opens the full-screen player for whatever is active. */
  openPlayer: (): Promise<void> => Native.openPlayer(),

  // Publisher mode
  /** Fetches the publisher and its stations, from the SDK's cache when it has them. */
  initializePublisher: (sdkKey: string): Promise<AtunwaPublisher> =>
    Native.initializePublisher(sdkKey),
  /** Fetches the publisher and its stations from the network, never from the cache. */
  refreshPublisher: (sdkKey: string): Promise<AtunwaPublisher> =>
    Native.refreshPublisher(sdkKey),
  /** Activates a station and opens the full-screen player. */
  loadStation: (assetKey: string): Promise<void> =>
    Native.loadStation(assetKey),
  /** Activates a station and starts playing it without opening the player. */
  activateStation: (assetKey: string): Promise<void> =>
    Native.activateStation(assetKey),

  // Playback
  play: (): Promise<void> => Native.play(),
  pause: (): Promise<void> => Native.pause(),
  /** Stops playback and clears the active station and the publisher config. */
  reset: (): Promise<void> => Native.reset(),

  /** Calls `listener` with every playback change. Returns a function that unsubscribes. */
  addPlaybackListener(
    listener: (playback: AtunwaPlayback) => void,
  ): () => void {
    const subscription = emitter.addListener('AtunwaMediaPlayback', event =>
      listener(event as AtunwaPlayback),
    );
    return () => subscription.remove();
  },
};

const idle: AtunwaPlayback = {
  state: 'idle',
  playWhenReady: false,
  isPlayerOpen: false,
};

/** The SDK's playback, kept up to date. */
export function usePlayback(): AtunwaPlayback {
  const [playback, setPlayback] = useState(idle);
  useEffect(() => AtunwaMedia.addPlaybackListener(setPlayback), []);
  return playback;
}

/** A stream is loaded: playing, paused, buffering, in an ad, or failed. */
export const hasStream = (p: AtunwaPlayback) =>
  p.state !== 'idle' && p.state !== 'initializing';

export const isPlaying = (p: AtunwaPlayback) =>
  p.playWhenReady &&
  ['loading', 'buffering', 'playing', 'adPlaying'].includes(p.state);

/** Show the mini player while a stream is loaded and the full-screen player isn't open. */
export const showsMiniPlayer = (p: AtunwaPlayback) =>
  hasStream(p) && !p.isPlayerOpen;

// Native views

const NativeLiveCard = requireNativeComponent<ViewProps>('AtunwaLiveCard');
const NativeMiniPlayer = requireNativeComponent<ViewProps>('AtunwaMiniPlayer');

/**
 * The SDK's "now playing" card. Always 16:9. It shows the active station or, before one is
 * active, the publisher's first station, which its play button starts in place. Render it after
 * `initializePlayer` or `initializePublisher`.
 */
export function AtunwaLiveCard({ style, ...props }: ViewProps) {
  return <NativeLiveCard style={[styles.liveCard, style]} {...props} />;
}

/**
 * The SDK's mini player bar. Its close button calls `reset()`. It's 62 points tall on iOS; on
 * Android it's taller, because it includes margins for its shadow.
 */
export function AtunwaMiniPlayer({ style, ...props }: ViewProps) {
  return <NativeMiniPlayer style={[styles.miniPlayer, style]} {...props} />;
}

const styles = StyleSheet.create({
  liveCard: { width: '100%', aspectRatio: 16 / 9 },
  miniPlayer: { width: '100%', height: Platform.OS === 'ios' ? 62 : 76 },
});
