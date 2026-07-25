# Clipboard Replacer

Personal Android app that rewrites copied links:

- `x.com` / `twitter.com` (and fix embeds) → `fixvx.com` / `fixupx.com` / custom domain
- YouTube → `youtu.be`, drops `si`, keeps timestamp (`t` / `start`)

## How it works

Android 10+ blocks background clipboard reads. With **Show notification** on, a foreground service listens for clipboard changes and posts a prompt. Tap it to open a brief focused activity that reads, rewrites, and writes the clipboard (using your last-chosen X host).

The ongoing notification opens the main screen for manual host selection. **Copy fixed URLs** also works while the app is open (has focus).

## Requirements

- Android Studio Ladybug+ (or equivalent AGP 8.7 toolchain)
- JDK 17
- Android SDK 35+

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install on a device/emulator:

```bash
./gradlew :app:installDebug
```

## Project layout

- `UrlRewriter` — pure URL transforms (unit tested)
- `ClipboardMonitorService` — FGS + clipboard listener + notifications
- `ClipboardFixActivity` — translucent focus activity for one-tap rewrite from the prompt
- `MainActivity` — toggle monitoring + manual fix / host selection
