# Gallery (plain-gallery)

Fast, private, ad-free photo gallery for Android. Native Kotlin + Jetpack Compose, no network access.
Product spec and roadmap: `docs/SPEC.md`.

## Stack (keep on latest stable unless noted)
- Kotlin 2.4 (AGP 9 built-in Kotlin), AGP 9.5, Gradle 9.8, JDK from Android Studio (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- Compose BOM 2026.09; **Material 3 pinned to 1.5.0-beta** for Expressive components (`gradle/libs.versions.toml`).
- Navigation 3, Room 3 (KSP, framework SQLite driver), Coil 3, Telephoto (zoom/subsampling), Media3 ExoPlayer + `media3-ui-compose`, DataStore.
- LiteRT 2.x (`CompiledModel`, CPU) for the bundled models: faces (YuNet + SFace, `core/faces/MODELS.md`) and OCR (PP-OCRv6 tiny, `core/text/MODELS.md`).
  Its `com.google.android.play` deps (proprietary AI/asset delivery for the unused `ModelProvider`) are excluded and its FOREGROUND_SERVICE* permissions removed in the manifest.
- minSdk 30 (trash/favorite MediaStore APIs, `GENERATION_MODIFIED`), target/compile 37.
- Manual DI: `DataGraph` (core:data), owned by `GalleryApplication`, exposed to Compose via `LocalDataGraph`.

## Modules
- `core:model` — pure Kotlin domain: `Media`, `Library` (cached views per snapshot, incl. `people` and `memories(today)`), `MediaCollection`, `SourceClassifier`, `SearchQueryParser`, `Memories` (on this day), `FaceClusterer`, `People`. Unit-tested.
- `core:faces` — `FaceEngine` (LiteRT: YuNet detect → 5-point align → SFace 128-d embedding); pure `YuNet` decoding and `FaceAlignment` are unit-tested. Models in `src/main/assets/faces`, rebuilt by `scripts/convert-face-models.py`.
- `core:text` — `TextEngine` (LiteRT: tiled DB detection → per-line CTC recognition, dynamic width via the classic `org.tensorflow.lite.Interpreter.resizeInput`, since `CompiledModel` cannot resize); pure `TextDetection`/`Recognizer` are unit-tested. Models rebuilt by `scripts/convert-text-models.py`.
- `core:data` — MediaStore scanner, Room index, `MediaRepository` (sync + optimistic hide/favorite), `MediaWriter` (create*Request), share/convert, settings, `FaceRepository` (face index in `faces.db`, clustering, names), `TextRepository` (`text.db`), `AlbumRepository` (`albums.db`), `MediaSaver` + `VideoTrimmer` for the creative tools.
- `core:ui` — theme, thumbnail pipeline, `MediaGrid` (pinch, scrubber, drag-select), `MediaBrowser`, share sheet, `MediaActions`.
- `feature:{library,viewer,search,tidy,settings,editor,create}` — screens (`create`: collage, Instagram grid, video trim, opened through `LocalCreateActions` from the selection toolbar and viewer); features never depend on each other (the viewer opens the editor through `ACTION_EDIT` + its own package, handled by `EditActivity`).
- `app` — Nav3 `NavDisplay` + shared transitions, tabs (`HorizontalPager`: swipe or tap), permissions; `ViewerActivity` handles VIEW/REVIEW intents (default viewer). `baselineprofile` — profile generator.
- Convention plugins live in `build-logic/convention` (`plaingallery.android.*`).

## Invariants
- **No network.** Manifest removes `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`. `scripts/check-permissions.sh <apk>` enforces an allowlist (CI runs it).
- **No secrets or PII in the repo.** Signing comes from `~/.gradle/gradle.properties` / CI env (`plaingallery.*`, `PLAINGALLERY_*`); see `docs/RELEASING.md`.
- No proprietary dependencies; APKs exclude the dependency-info blob.
- **Speed first.** UI renders from the Room index, never waits on MediaStore. Sync is an incremental `_ID`/`GENERATION_MODIFIED` diff.
  All screens read one `Library` snapshot; first frames use `MediaRepository.snapshot()` so nothing flashes empty.
- Thumbnails come from `ContentResolver.loadThumbnail` at one size (`THUMBNAIL_PX`), memory cache only, no disk cache.
- Deletes go to the system Trash (`createTrashRequest`), applied optimistically with Undo. Never delete permanently without confirmation.
- `kept` table is user data (incoming items promoted to Photos): migrations must preserve it; the `media` table is rebuildable.
- Faces live in a separate `faces.db` (`FaceDatabase`): `person` (names, hidden) is user data and must survive migrations; `face`/`face_scan` are rebuildable but slow (whole library re-analyzed).
- `albums.db` (user albums) is user data: keep both tables in migrations. `text.db` is rebuildable.
- Creative tools and the editor only ever add new files (MediaStore insert with `IS_PENDING`); originals are never modified.
- Faces and OCR share one min-priority `analysisDispatcher` thread so they take turns per photo.
- Face indexing runs only while the app is in the foreground, one photo at a time on a min-priority thread with 2 LiteRT threads, saving every few photos; never in a service or WorkManager (no `WAKE_LOCK`). Turning "Group faces" off deletes all face data.
- Clustering errs towards splitting (assign 0.48, merge 0.55 cosine on SFace); users join groups by giving the same name. Groups the user named or hid are never auto-merged with each other.
- String resource names must be unique across modules (they merge in the app). Check:
  `grep -ho 'name="[^"]*"' */*/src/main/res/values/strings.xml app/src/main/res/values/strings.xml | sort | uniq -d`.
- Every user-visible string exists in `values` (English) and `values-in` (Indonesian).
- Share conversion rules live only in `SharePlanner` (pure, unit-tested); `MediaConverter` executes them (ImageDecoder → JPEG/PNG/WebP, Media3 Transformer → H.264/AAC MP4). A failed conversion sends the original. Default preset is Recommended.
- Theme follows the system (light/dark, dynamic color); no in-app override. The viewer and editor are always dark.
- Screens without a `Surface`/`Scaffold` must set `LocalContentColor` or explicit colors, or text and icons render black in dark mode.
- Grid pinch: live `graphicsLayer` scale around the fingers; the level switches when cells reach ~60% (log scale) of the next size, with `requestScrollToItem` keeping the item under the fingers in the same frame. Layouts for every grouping are prebuilt; `animateItem` placement is off while zooming.
- Editor never modifies originals: it saves a copy (same folder if DCIM/ or Pictures/, else `Pictures/Edited/`) and copies date/camera/GPS EXIF. All geometry and drawing go through `drawEdit`, shared by preview and export; crop math is pure and unit-tested (`EditModel.kt`).

## Gotchas
- `LazyGridItemInfo.offset` excludes top content padding; hit-test with `viewportStartOffset` (see `GridGestures.kt`).
  Screen header items precede grid entries, so grid index = header count + entry index.
- Video: some hardware decoders (and the emulator's goldfish decoder) fail. `Codecs.selector` puts software decoders first after one failure; used by `ViewerPlayer` (PlaybackException 4001–4003) and `MediaConverter` (ExportException 3001–3002; different code ranges).
- Device testing: adb tap helpers break in zsh (no word splitting); write them as bash scripts. adb can't multi-touch: test pinch with a throwaway UiAutomator test (`UiObject.performTwoPointerGesture`) in `baselineprofile`, run against the `benchmarkRelease` build. Swipes starting within ~80 px of the left/right edge trigger system Back.
- Stress testing: `monkey -p app.plaingallery --pct-syskeys 0 --pct-flip 0` on the release build (same signing key, so `install -r` keeps the `kept` table). Disable the shade first (`cmd statusbar send-disable-flag statusbar-expand`, undo with `none`). It can change in-app settings and save editor copies: check and clean up afterwards. Launch with the launcher intent (`monkey -p app.plaingallery -c android.intent.category.LAUNCHER 1`); `am start -n` creates extra tasks.
- Spammed taps land during transitions: only the top Nav3 entry may push/pop (`GalleryApp.kt`), `MediaActions` runs one system request at a time and undoes the optimistic change if the screen leaves before the result.
- MediaStore renames trashed files to `.trashed-<expiry>-<name>`; show the original name.
- Home tabs are a `HorizontalPager` keyed by tab; neighbours are composed only 1 s after start (`beyondViewportPageCount`) to keep cold start to one grid. Search focuses its field only when its page has settled.
- `ImageDecoder` sizes and crops are in upright (EXIF-rotated) coordinates; face boxes are stored as fractions of that upright image.

## Release & store assets
- CI: `.github/workflows/ci.yml` (tests, lint, release build, permission check). Release: push tag `vX.Y.Z` matching `versionName` → `release.yml`.
- Store listing (Play): `fastlane/metadata/android/{en-US,id}`; screenshots there are reused by the README.
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
Run unit tests and a release build after changes; for UI changes, install the release build on the owner's phone over USB (the only test device; no emulator) and check the touched flow, including rapid repeated taps. Never trash, favorite or keep real photos without undoing it; deny system dialogs.
