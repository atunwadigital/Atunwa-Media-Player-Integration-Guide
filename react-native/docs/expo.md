# Using the SDK from an Expo app

The Atunwa Media SDK is native code, so it doesn't run in **Expo Go**. You can use it in an Expo
app with a [development build](https://docs.expo.dev/develop/development-builds/introduction/),
as long as you keep the app's native projects yourself.

## Why the native projects have to stay

With Expo's default setup, Continuous Native Generation, `npx expo prebuild` regenerates `ios/`
and `android/` from `app.json` and config plugins. The module in
[Getting started](getting-started.md) is made of native files and Xcode project changes, and a
regeneration would remove them.

The usual way to keep native changes across regenerations is an Expo module or a config plugin.
Neither works for the iOS SDK yet: Expo modules are built as CocoaPods pods, and the Atunwa iOS SDK
is distributed through Swift Package Manager only, so a pod can't depend on it.

## What to do

1. Generate the native projects once:

   ```bash
   npx expo prebuild
   ```

2. Commit `ios/` and `android/`, and from now on don't run `prebuild --clean`. Your app now
   builds like a plain React Native app, while still using Expo's libraries, `expo-dev-client`
   and EAS Build.
3. Follow [Getting started](getting-started.md) from step 1, using the generated `ios/` and
   `android/` projects.
4. Build a development client and run it:

   ```bash
   npx expo run:ios
   npx expo run:android
   ```

With the native projects committed, EAS Build uses them as they are instead of regenerating them.

## Things to watch

| | |
|---|---|
| **Config plugins** | Plugins listed in `app.json` only change the native projects when `prebuild` runs. After you add a library that needs one, run `npx expo prebuild` (without `--clean`) and review the diff before committing |
| **Other audio libraries** | `expo-av`, `expo-audio` and `expo-video` manage the audio session and media controls too. Pause the Atunwa SDK before playing other media with them. See [Troubleshooting](troubleshooting.md#playback) |
| **Expo SDK upgrades** | Upgrades that change the native templates need those changes applied to your committed projects by hand. Expo's upgrade guides list them |

---

**Next:** [Troubleshooting](troubleshooting.md)
