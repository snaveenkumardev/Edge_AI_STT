# TrailApp

An [Expo Router](https://docs.expo.dev/router/introduction) app (Expo SDK 57) targeting iOS, Android, and web from one codebase.

## Prerequisites

This project depends on native modules (`react-native-audio-api`, `react-native-executorch`) that are wired in via config plugins in [app.json](app.json). Because of this custom native code, **the app cannot run in Expo Go** — you need a development build.

## Get started

1. Install dependencies (`yarn.lock` is the source of truth, not `package-lock.json`)

   ```bash
   yarn install
   ```

2. Build and install a development build

   Native code changed (or this is your first run), so build and launch the dev client on a simulator/emulator or device:

   ```bash
   yarn ios       # build + run on iOS simulator/Device
   yarn android   # build + run on Android emulator/Device
   ```

   This compiles the native project and installs an [`expo-dev-client`](https://docs.expo.dev/develop/development-builds/introduction/) build with your custom native modules included.

3. For subsequent runs, start the dev server and reload the existing development build (no need to rebuild unless native dependencies or config plugins change):

   ```bash
   yarn start
   ```

### Rebuilding

Re-run `yarn ios` / `yarn android` whenever you add/update a native dependency, change `app.json` plugin config, or the generated `ios`/`android` folders are out of date.
