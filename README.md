# Force Orientation

An Android app that forces all apps to respect the system's user rotation setting using an invisible overlay.

## Features

- 🔄 Forces apps to follow your system rotation lock (Portrait or Landscape)
- 🚀 Auto-start on boot option
- 🔋 Minimal battery impact (no WakeLock)
- 📱 Works on Android 7.0+ (API 24+)

## How It Works

The app creates an invisible 1x1 pixel overlay window with `SCREEN_ORIENTATION_USER`. This tells the Android WindowManager to respect the user's system rotation setting, overriding any underlying app's orientation preference.

## Usage

1. Install the app
2. Grant "Display over other apps" permission
3. Toggle "Enable Global Force Sync" ON
4. Lock your device rotation using system settings
5. All apps will now respect your rotation lock!

## Permissions

- `SYSTEM_ALERT_WINDOW` - Required for overlay window
- `FOREGROUND_SERVICE` - Keep service running
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot
- `POST_NOTIFICATIONS` - Service notification

## Building

```bash
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT License
