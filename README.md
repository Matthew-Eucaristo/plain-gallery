# Gallery

A photo gallery for Android that just works: no ads, no backup nagging, no internet permission.

- **Photos** — your camera roll and other memories, grouped by day/month/year; pinch to change density, drag the scrubber to fly through years.
- **Inbox** — WhatsApp images, screenshots, downloads and Telegram media, kept apart from your memories, with one-tap clean-up and a swipe-to-tidy mode.
- **Search** — understands Indonesian and English: `wa juli 2024`, `screenshot kemarin`, `lebaran 2023`, `video favorit`, or part of a file name.
- **Share** — one tap to WhatsApp or the app you used last; choose Original, Compatible (JPEG) or Small (no location).
- Fast by design: local index, system thumbnail cache, optimistic deletes with undo, Baseline Profile. Release APK ≈ 5 MB.

Requires Android 11+. Built with Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, Room 3, Coil 3, Media3.

See [docs/SPEC.md](docs/SPEC.md) for the product spec and roadmap, and [AGENTS.md](AGENTS.md) for architecture and contributor notes.

## Build

```bash
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```
