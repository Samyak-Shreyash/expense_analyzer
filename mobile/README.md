# Expense Analyzer mobile app

Expo (managed workflow), TypeScript strict and Expo Router, for iOS and Android.

## Run against a local backend

1. Start the backend from `backend/` with `./gradlew bootRun`.
2. In `mobile/`, run `npm ci` and copy `.env.example` to `.env.local`. Set the URL for your target:
   - iOS simulator: `http://localhost:8080/api`
   - Android emulator: `http://10.0.2.2:8080/api`
   - Physical device: `http://<your LAN address>:8080/api`
3. Run `npm run ios` or `npm run android`.

Staging and production builds set `EXPO_PUBLIC_API_BASE_URL` in their build profile. `EXPO_PUBLIC_` values are compiled into the app, so never put secrets in them.

## Install on your iPhone

To install a standalone build on a physical iPhone from a Mac (free Apple ID, no Expo Go), follow
[docs/deployment/ios-device.md](../docs/deployment/ios-device.md).

## Checks

`npm run ci` runs ESLint, Prettier, the type check and Jest.
