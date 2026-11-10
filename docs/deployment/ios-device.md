# Installing the mobile app on an iPhone

Build the Expo app on a Mac with Xcode and install it straight onto your own iPhone.
No Expo Go, no paid Apple Developer account, no App Store.

The backend must already be running and reachable over HTTPS (see [vps.md](vps.md)).

## What a free Apple ID gives you

- The app **expires after 7 days**. Plug the phone in and run the build again. Data on the
  phone is kept.
- At most 3 of your own apps on the phone at once.
- Face ID, Keychain (secure store) and networking all work. Push notifications and some other
  capabilities need the paid program.

## 1. One-time Mac setup

1. Install **Xcode** from the Mac App Store and open it once to finish component installation.
   Check that the full app is selected, not just the command-line tools:
   ```bash
   xcode-select -p
   # want: /Applications/Xcode.app/Contents/Developer
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer   # if it shows CommandLineTools
   ```
2. Install Node (LTS) and CocoaPods: `brew install node cocoapods`.
3. Sign in: **Xcode → Settings… (⌘ ,) → Accounts → +** with your Apple ID. This creates a free
   *Personal Team*. The Settings menu is in the macOS menu bar at the top of the screen.
4. Create a signing certificate: in the same Accounts tab, select your Apple ID →
   **Manage Certificates… → + → Apple Development**. Verify:
   ```bash
   security find-identity -v -p codesigning
   # want: 1 valid identities found  ("Apple Development: …")
   ```
   If it says `0 valid identities` although the certificate exists, install Apple's
   *Worldwide Developer Relations – G3* intermediate certificate from
   https://www.apple.com/certificateauthority/ and check again.

## 2. One-time iPhone setup

1. Connect the iPhone to the Mac with a **data** USB cable, unlock it and tap **Trust**.
2. In Xcode open **Window → Devices and Simulators** (⇧⌘2). Wait until
   "Preparing device for development" finishes (keep the phone unlocked).
3. On the iPhone: **Settings → Privacy & Security → Developer Mode → On**, restart, confirm.
   The switch only appears after step 2.
4. Check the Mac sees it: `xcrun devicectl list devices`.

Optional: tick **Connect via network** in Devices and Simulators to build over Wi-Fi later.

## 3. Project setup

From the repo root on the Mac:

```bash
cd mobile
npm ci
cp .env.example .env.local
```

Edit `mobile/.env.local`. Expo reads `.env.local`, **not** `.env.example`:

```dotenv
EXPO_PUBLIC_API_BASE_URL=https://<host>.<tailnet>.ts.net/api
```

- Point it at the backend's Tailscale Funnel origin **plus `/api`**. The API client appends
  paths such as `/auth/login`, and every backend route starts with `/api`.
- Release builds refuse plain `http://`.
- `EXPO_PUBLIC_` values are compiled into the app: never put secrets here, and rebuild after
  changing them.

Give the app a unique bundle ID in `mobile/app.json` (once):

```json
"ios": {
  "icon": "./assets/expo.icon",
  "bundleIdentifier": "com.<yourname>.expenseanalyzer"
}
```

## 4. Build and install

```bash
npx expo run:ios --device --configuration Release
```

Pick your iPhone from the list (or pass its name: `--device "Your iPhone"`). The JavaScript is
bundled into the app, so it runs without the Mac afterwards.

The command generates `mobile/ios/` from `app.json`. It is git-ignored; don't commit it.

**First install only:** iOS blocks apps from an untrusted developer. On the iPhone go to
**Settings → General → VPN & Device Management**, tap your Apple ID, then **Trust**.

### If signing fails

`No code signing certificates are available` or a provisioning error:

1. Redo step 1.4 and confirm `security find-identity` shows a valid identity.
2. `open ios/*.xcworkspace` → select the project → **Expense Analyzer** target →
   **Signing & Capabilities** → tick *Automatically manage signing* → Team =
   *Your Name (Personal Team)*.
3. If Xcode rejects the bundle ID, change it to something unique in both Xcode and `app.json`.
4. Choose the iPhone in Xcode's toolbar and press **⌘R** once, then use the `npx` command again.

## 5. Day-to-day

| Task | Command |
|---|---|
| Install a fresh standalone build / renew after 7 days | `npx expo run:ios --device --configuration Release` |
| Develop with live reload (needs the Mac's dev server running) | `npx expo run:ios --device` |
| Restart the dev server after changing `.env.local` | `npx expo start --clear` |

Don't scan the QR code from a plain `npx expo start`: that opens Expo Go. For a dev launcher
in your own app, run `npx expo install expo-dev-client`, rebuild, and start the server with
`npx expo start --dev-client`.

## 6. Checking the backend connection

From the Mac:

```bash
curl -i https://<host>.<tailnet>.ts.net/api/transactions
```

| Response | Meaning |
|---|---|
| `401` with `www-authenticate: Bearer` | Backend reached |
| `502` / timeout | Funnel or containers down on the server |
| DNS error | Wrong host |

Note that Spring answers `401` for unknown paths too, because it authenticates first, so `curl` can't
tell you whether `/api` is missing. Signing in on the phone can: a wrong path fails even with
correct credentials.

In a Debug build, press **`j`** in the Metro terminal to open React Native DevTools. The
**Network** tab shows each request's full URL, status and `X-Correlation-ID`. Find the same
request on the server with:

```bash
docker logs backend 2>&1 | grep '<correlation-id>'
```

## 7. Logs

- **Debug build:** `console.*` output appears in the Metro terminal and in DevTools (`j`).
- **Release build:** only errors reach the device log. Open **Console.app** on the Mac, select
  the iPhone, press **Start** and filter by the app name. Crash reports are under
  Xcode → Devices and Simulators → **View Device Logs**.
- The repo's logging rule applies to the app: never log PII, account numbers, transaction
  descriptions or amounts. Wrap debugging-only logs in `if (__DEV__)`.
