# Gallery (plain-gallery)

Fast, private, ad-free photo gallery for Android. Native Kotlin + Jetpack Compose, no network access.
Product spec and roadmap: `docs/SPEC.md`.

## Stack (keep on latest stable unless noted)
- Kotlin 2.4 (AGP 9 built-in Kotlin), AGP 9.5, Gradle 9.8, JDK from Android Studio (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- Compose BOM 2026.09; **Material 3 pinned to 1.5.0-beta** for Expressive components (`gradle/libs.versions.toml`).
- Navigation 3, Room 3 (KSP, framework SQLite driver), Coil 3, Telephoto (zoom/subsampling), Media3 ExoPlayer + `media3-ui-compose`, DataStore.
- minSdk 30 (trash/favorite MediaStore APIs, `GENERATION_MODIFIED`), target/compile 37.
- Manual DI: `DataGraph` (core:data), owned by `GalleryApplication`, exposed to Compose via `LocalDataGraph`.

## Modules
- `core:model` — pure Kotlin domain: `Media`, `Library` (cached views per snapshot), `MediaCollection`, `SourceClassifier`, `SearchQueryParser`. Unit-tested.
- `core:data` — MediaStore scanner, Room index, `MediaRepository` (sync + optimistic hide/favorite), `MediaWriter` (create*Request), share/convert, settings.
- `core:ui` — theme, thumbnail pipeline, `MediaGrid` (pinch, scrubber, drag-select), `MediaBrowser`, share sheet, `MediaActions`.
- `feature:{library,viewer,search,tidy,settings}` — screens; features never depend on each other.
- `app` — Nav3 `NavDisplay` + shared transitions, tabs, permissions; `ViewerActivity` handles VIEW/REVIEW intents (default viewer). `baselineprofile` — profile generator.
- Convention plugins live in `build-logic/convention` (`plaingallery.android.*`).

## Invariants
- **No network.** Manifest removes `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`. `scripts/check-permissions.sh <apk>` enforces an allowlist (CI runs it).
- **No secrets or PII in the repo.** Signing comes from `~/.gradle/gradle.properties` / CI env (`plaingallery.*`, `PLAINGALLERY_*`); see `docs/RELEASING.md`.
- No proprietary dependencies (F-Droid eligible); APKs exclude the dependency-info blob.
- **Speed first.** UI renders from the Room index, never waits on MediaStore. Sync is an incremental `_ID`/`GENERATION_MODIFIED` diff.
  All screens read one `Library` snapshot; first frames use `MediaRepository.snapshot()` so nothing flashes empty.
- Thumbnails come from `ContentResolver.loadThumbnail` at one size (`THUMBNAIL_PX`), memory cache only, no disk cache.
- Deletes go to the system Trash (`createTrashRequest`), applied optimistically with Undo. Never delete permanently without confirmation.
- `kept` table is user data (incoming items promoted to Photos): migrations must preserve it; the `media` table is rebuildable.
- String resource names must be unique across modules (they merge in the app). Check:
  `grep -ho 'name="[^"]*"' */*/src/main/res/values/strings.xml app/src/main/res/values/strings.xml | sort | uniq -d`.
- Every user-visible string exists in `values` (English) and `values-in` (Indonesian).
- Share conversion rules live only in `SharePlanner` (pure, unit-tested); `MediaConverter` executes them (ImageDecoder → JPEG/PNG/WebP, Media3 Transformer → H.264/AAC MP4). A failed conversion sends the original. Default preset is Recommended.
- Theme follows the system (light/dark, dynamic color); no in-app override.

## Gotchas
- `LazyGridItemInfo.offset` excludes top content padding; hit-test with `viewportStartOffset` (see `GridGestures.kt`).
  Screen header items precede grid entries, so grid index = header count + entry index.
- Video: some hardware decoders (and the emulator's goldfish decoder) fail. `Codecs.selector` puts software decoders first after one failure; used by `ViewerPlayer` (PlaybackException 4001–4003) and `MediaConverter` (ExportException 3001–3002; different code ranges).
- Emulator testing: adb tap helpers break in zsh (no word splitting); write them as bash scripts.
- MediaStore renames trashed files to `.trashed-<expiry>-<name>`; show the original name.

## Release & store assets
- CI: `.github/workflows/ci.yml` (tests, lint, release build, permission check). Release: push tag `vX.Y.Z` matching `versionName` → `release.yml`.
- Store listing (Play + F-Droid): `fastlane/metadata/android/{en-US,id}`; screenshots there are reused by the README.
- Store title is "Plain Gallery"; the launcher label is "Gallery".

## Commands
```bash
./gradlew testDebugUnitTest          # unit tests (model, data)
./gradlew :app:assembleDebug         # debug APK
./gradlew :app:assembleRelease       # R8 release APK (upload key if configured, else debug key)
./gradlew :app:bundleRelease         # AAB for Google Play
./gradlew lintDebug                  # lint all modules
./gradlew :app:generateReleaseBaselineProfile   # needs a device/emulator with photos
```

## Verification expectations
Run unit tests and a release build after changes; for UI changes, install on an emulator (`pg_api37`, Android 17) with sample media and check the touched flow.
