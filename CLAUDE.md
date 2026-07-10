# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Expo version notice

This project uses **Expo SDK 57**, which changed significantly from earlier SDKs (native tabs, new APIs). Before writing any Expo-related code, consult the versioned docs at https://docs.expo.dev/versions/v57.0.0/ rather than relying on training knowledge of older Expo versions.

## Commands

```bash
yarn install         # install dependencies (yarn.lock is the source of truth, not package-lock.json)
yarn start           # start Metro/Expo dev server
yarn ios             # start + open iOS simulator
yarn android         # start + open Android emulator
yarn web             # start + open web
yarn lint            # expo lint (ESLint)
yarn reset-project   # moves starter code to app-example/ and creates a blank src/app — destructive, one-way
```

There is no test suite configured in this repo currently.

## Architecture

This is an Expo Router (file-based routing) app written in TypeScript, targeting iOS, Android, and web from one codebase.

- **Routing root is `src/app`**, not the conventional top-level `app/`. `main` in [package.json](package.json) points to `expo-router/entry`, and Expo Router is configured to use `src/app` as the routes directory. Typed routes are enabled (`experiments.typedRoutes` in [app.json](app.json)), so route params/hrefs are type-checked.
- **Path aliases**: `@/*` → `src/*`, `@/assets/*` → `assets/*` (see [tsconfig.json](tsconfig.json)). Always import via `@/...` rather than relative paths across directories.
- **`src/app/_layout.tsx`** is the root layout: wraps the app in Expo Router's `ThemeProvider` (light/dark based on `useColorScheme`), renders the animated splash overlay, then `AppTabs`.
- **Platform-specific file resolution is used heavily.** Several components have a default implementation plus a `.web.tsx` (or `.web.ts`) counterpart that Metro/webpack picks automatically for web builds:
  - `components/app-tabs.tsx` (native, uses `expo-router/unstable-native-tabs` → real native tab bar) vs `components/app-tabs.web.tsx` (web, uses `expo-router/ui` `Tabs`/`TabList`/`TabTrigger` to build a custom floating tab bar).
  - `components/animated-icon.tsx` vs `components/animated-icon.web.tsx` (web version pairs with `animated-icon.module.css` for a CSS-driven logo background instead of a Reanimated gradient view).
  - `hooks/use-color-scheme.ts` vs `hooks/use-color-scheme.web.ts` (web version defers to `react-native`'s hook only after client hydration, to support static rendering).
  When changing behavior for one platform, check whether a sibling `.web.*` file needs the equivalent change.
- **Theming** flows through `src/constants/theme.ts` (`Colors.light` / `Colors.dark`, `Fonts`, `Spacing` scale, `MaxContentWidth`, `BottomTabInset`) → `hooks/use-theme.ts` (resolves the active color scheme to a `Colors` entry) → the `ThemedText` / `ThemedView` components, which take a semantic `type`/`themeColor` prop instead of raw colors. Prefer extending `Colors`/`Spacing` and using `ThemedText`/`ThemedView` over hardcoding colors or spacing values in a component.
- **`src/global.css`** defines CSS custom properties for font stacks (`--font-display`, `--font-mono`, etc.) consumed by `Fonts.web` in `theme.ts`; it's imported once from `theme.ts` so it's only relevant on web.
- Splash screen handling (`_layout.tsx` + `components/animated-icon.tsx`) calls `SplashScreen.preventAutoHideAsync()` at module scope and hides it manually once the animated overlay has laid out — don't add a second `preventAutoHideAsync`/`hideAsync` pair elsewhere.
- `react-native-executorch` and its Expo resource-fetcher are installed dependencies but not yet wired into any screen — treat them as available for on-device ML work, not as existing functionality to preserve.
- The `app-example/` directory (created by `yarn reset-project`) and native `ios/`/`android/` directories are gitignored/generated; don't hand-edit generated native projects unless intentionally ejecting.
