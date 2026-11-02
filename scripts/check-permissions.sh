#!/usr/bin/env bash
# Fails if the APK requests network (or other unexpected) permissions. Usage: check-permissions.sh <apk>
set -euo pipefail
apk="$1"
aapt2="$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/*/aapt2 | sort -V | tail -1)"
permissions="$("$aapt2" dump permissions "$apk" | sed -n "s/.*uses-permission: name='\([^']*\)'.*/\1/p" | sort)"
echo "$permissions"
allowed="android.permission.ACCESS_MEDIA_LOCATION
android.permission.MANAGE_MEDIA
android.permission.READ_EXTERNAL_STORAGE
android.permission.READ_MEDIA_IMAGES
android.permission.READ_MEDIA_VIDEO
android.permission.READ_MEDIA_VISUAL_USER_SELECTED
app.plaingallery.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
unexpected="$(comm -23 <(echo "$permissions") <(echo "$allowed" | sort))"
if [ -n "$unexpected" ]; then
  echo "Unexpected permissions (the app must stay offline):" >&2
  echo "$unexpected" >&2
  exit 1
fi
