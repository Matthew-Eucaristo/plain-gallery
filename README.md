# Gallery

A photo gallery for Android that just works: no ads, no backup nagging, no internet permission.

- **Photos** — your camera roll and other memories, grouped by day/month/year; pinch to change density, drag the scrubber to fly through years.
- **Inbox** — WhatsApp images, screenshots, downloads and Telegram media, kept apart from your memories, with one-tap clean-up and a swipe-to-tidy mode.
- **Search** — understands Indonesian and English: `wa juli 2024`, `screenshot kemarin`, `lebaran 2023`, `video favorit`, or part of a file name.
- **Share** — one tap to WhatsApp or the app you used last. *Recommended* (default) converts only what other apps can't open (HEIC/AVIF/RAW → JPEG, HEVC/VP9/AV1/WebM/MKV → H.264 MP4); also *Original*, *Small* (1600 px photos, 720p video) and *Custom* (JPG/PNG/WebP, photo size, 1080p/720p/480p).
- **Default viewer** — open photos and videos from any app or the camera's preview button.
- **Formats** — JPEG, PNG, WebP, GIF, HEIC/HEIF, AVIF, DNG, BMP; MP4, MOV, 3GP, MKV, WebM, AVI with H.264, HEVC, VP8/9, AV1, MPEG-4. Ultra HDR on Android 14+.
- Follows system light/dark and wallpaper colors.
- Fast by design: local index, system thumbnail cache, optimistic deletes with undo, Baseline Profile. Release APK ≈ 5 MB.

Requires Android 11+. Built with Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, Room 3, Coil 3, Media3.

See [docs/SPEC.md](docs/SPEC.md) for the product spec and roadmap, and [AGENTS.md](AGENTS.md) for architecture and contributor notes.

## Build

```bash
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

## Contributing

Issues and pull requests are welcome. Read [AGENTS.md](AGENTS.md) for the architecture, invariants (no network, speed first, strings in English and Indonesian) and the checks to run before sending a change.

## License

[Apache License 2.0](LICENSE)
