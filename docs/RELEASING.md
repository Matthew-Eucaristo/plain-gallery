# Releasing

## Signing

Releases are signed with an **upload key**; Google Play re-signs them with its own app signing key (Play App Signing).

Release builds read the key from Gradle properties or environment variables, never from the repo:

| Gradle property (`~/.gradle/gradle.properties`) | Environment variable (CI) |
|---|---|
| `plaingallery.keystore` (path to `.jks`) | `PLAINGALLERY_KEYSTORE` |
| `plaingallery.keystore_password` | `PLAINGALLERY_KEYSTORE_PASSWORD` |
| `plaingallery.key_alias` | `PLAINGALLERY_KEY_ALIAS` |
| `plaingallery.key_password` | `PLAINGALLERY_KEY_PASSWORD` |

Without them, release builds fall back to the debug key (fine for local testing).

**Back up the keystore and its password** (password manager, plus an offline copy). If the upload key is lost, Play can reset it, but sideloaded and GitHub APKs signed with it can't be updated in place.

### GitHub secrets for the release workflow

Already configured. To set or rotate them, run from the repo folder (they upload the key to GitHub's encrypted secrets):

```bash
base64 -i "$(grep '^plaingallery.keystore=' ~/.gradle/gradle.properties | cut -d= -f2)" | gh secret set PLAINGALLERY_KEYSTORE_BASE64
grep '^plaingallery.keystore_password=' ~/.gradle/gradle.properties | cut -d= -f2 | gh secret set PLAINGALLERY_KEYSTORE_PASSWORD
gh secret set PLAINGALLERY_KEY_ALIAS --body upload
grep '^plaingallery.key_password=' ~/.gradle/gradle.properties | cut -d= -f2 | gh secret set PLAINGALLERY_KEY_PASSWORD
```

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`; add `fastlane/metadata/android/{en-US,id}/changelogs/<versionCode>.txt`.
2. Commit, then tag and push: `git tag v0.2.0 && git push origin v0.2.0`.
3. The **Release** workflow attaches `plain-gallery-v0.2.0.apk` and `SHA256SUMS` to a GitHub Release and keeps the `.aab` as a workflow artifact for Google Play.

Local equivalent: `./gradlew :app:assembleRelease :app:bundleRelease` →
`app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`.

## Google Play (first time)

1. Play Console → **Create app**: name "Plain Gallery", free, app (not game).
2. **Testing → Internal testing → Create release**: upload `app-release.aab`. Accept **Play App Signing** (Google manages the app signing key; your keystore stays the upload key).
3. **App content**:
   - Privacy policy: `https://github.com/Matthew-Eucaristo/plain-gallery/blob/main/PRIVACY.md`
   - Ads: no ads. Target audience: 13+ (or all ages). Content rating questionnaire: utility, no user-generated content.
   - **Data safety**: no data collected, no data shared; the app has no internet access.
   - **Photo and video permissions** declaration: core functionality is a gallery that manages the user's photos and videos (browse, organize, share, delete), so it needs persistent broad access.
4. **Store listing**: texts and graphics are in `fastlane/metadata/android/en-US` (and `id` for Indonesian): `images/icon.png` (512×512), `images/featureGraphic.png` (1024×500), `images/phoneScreenshots/*.png`.
5. Personal developer accounts created after November 2023 must run a **closed test with at least 12 testers for 14 days** before applying for production access.
