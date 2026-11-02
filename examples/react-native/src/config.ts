// The Atunwa key this build uses, from atunwa.config.json (git-ignored; `npm install` creates it
// from atunwa.config.example.json). Atunwa issues both kinds of key, and both are safe to ship in
// an app.
import values from '../atunwa.config.json';

export type AtunwaConfig =
  /** One station, identified by its player API key. */
  | { mode: 'single'; playerApiKey: string }
  /** Every station a publisher has, identified by the publisher's SDK key. */
  | { mode: 'publisher'; publisherSdkKey: string }
  /** The build has no usable key. `problem` says what to fix. */
  | { mode: 'missing'; problem: string };

export function readConfig(): AtunwaConfig {
  const mode = (values.ATUNWA_APP_MODE ?? '').trim();
  const playerKey = (values.ATUNWA_PLAYER_KEY ?? '').trim();
  const publisherKey = (values.ATUNWA_PUBLISHER_SDK_KEY ?? '').trim();

  switch (mode.toLowerCase()) {
    case 'single':
      return playerKey
        ? { mode: 'single', playerApiKey: playerKey }
        : {
            mode: 'missing',
            problem:
              'ATUNWA_APP_MODE is single, but ATUNWA_PLAYER_KEY is empty.',
          };
    case 'publisher':
      return publisherKey
        ? { mode: 'publisher', publisherSdkKey: publisherKey }
        : {
            mode: 'missing',
            problem:
              'ATUNWA_APP_MODE is publisher, but ATUNWA_PUBLISHER_SDK_KEY is empty.',
          };
    default:
      return {
        mode: 'missing',
        problem: `ATUNWA_APP_MODE must be single or publisher, not "${mode}".`,
      };
  }
}
