<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="96" alt="Gallery icon">
</p>

<h1 align="center">Plain Gallery</h1>

<p align="center">
  A photo gallery for Android that just works.<br>
  No ads, no backup nagging, no internet permission.
</p>

<p align="center">
  <a href="https://github.com/Matthew-Eucaristo/plain-gallery/actions/workflows/ci.yml"><img src="https://github.com/Matthew-Eucaristo/plain-gallery/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License: Apache 2.0"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84" alt="Android 11+">
  <img src="https://img.shields.io/badge/internet-none-success" alt="No internet permission">
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="19%" alt="Photos">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="19%" alt="Inbox">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="19%" alt="Share">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="19%" alt="Tidy">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="19%" alt="Albums, dark">
</p>

## Why

Phone galleries show ads; cloud galleries keep asking to back up until your free storage is full. Plain Gallery is just a gallery: fast, simple enough for parents, and physically unable to upload anything because it has no network access.

## Features

- **Photos and Inbox, kept apart.** Camera photos in *Photos*; WhatsApp images, screenshots, downloads and Telegram media in *Inbox*, with one-tap clean-up of old items and a swipe-to-tidy mode (left to delete, right to keep).
- **Fast.** Local index with incremental sync, system thumbnail cache, Baseline Profile, optimistic deletes with Undo. Pinch to change density, drag the scrubber through years.
- **Search that understands you**, in English and Indonesian: `wa july 2024`, `screenshot yesterday`, `video favorites`, `eid 2023`, or part of a file name.
- **Share in one tap** to WhatsApp or the app you used last. *Recommended* (default) converts only what other apps can't open: HEIC/AVIF/RAW → JPEG, HEVC/VP9/AV1/WebM/MKV → H.264 MP4. Also *Original*, *Small* (1600 px photos, 720p video) and *Custom* (JPG/PNG/WebP, photo size, 1080p/720p/480p).
- **Opens everything**: JPEG, PNG, WebP, GIF, HEIC/HEIF, AVIF, DNG, BMP; MP4, MOV, 3GP, MKV, WebM, AVI with H.264, HEVC, VP8/9, AV1, MPEG-4. Ultra HDR on Android 14+.
- **Default viewer** for photos and videos from any app and from the camera's preview button.
- **Safe**: deletes go to the system Trash for 30 days. Follows system light/dark and wallpaper colors.

## Install

- **GitHub Releases**: download the APK from [Releases](https://github.com/Matthew-Eucaristo/plain-gallery/releases).
- **Google Play**: in review (closed testing first).

Requires Android 11 or later.

## Privacy

No internet permission, no accounts, no analytics, no ads. CI fails the build if the APK ever requests network access. See [PRIVACY.md](PRIVACY.md).

## Build

```bash
./gradlew :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Kotlin, Jetpack Compose, Material 3 Expressive, Navigation 3, Room 3, Coil 3, Telephoto, Media3 (ExoPlayer, Transformer).

- [docs/SPEC.md](docs/SPEC.md) — product spec and roadmap (Indonesian)
- [AGENTS.md](AGENTS.md) — architecture, invariants and checks
- [docs/RELEASING.md](docs/RELEASING.md) — signing, Google Play

## Contributing

Issues and pull requests are welcome. Read [AGENTS.md](AGENTS.md) first: the app must stay offline and fast, and every string needs English and Indonesian.

## License

[Apache License 2.0](LICENSE)
