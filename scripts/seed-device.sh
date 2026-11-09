#!/usr/bin/env bash
# Seeds the connected emulator/phone for the Say the Goal demos. Re-runnable before every take.
#
#   scripts/seed-device.sh            photos + Maya contact
#   scripts/seed-device.sh photos     push the two dog photos, dated yesterday, and rescan media
#   scripts/seed-device.sh contact    add the Maya contact (skips if she already exists)
#   scripts/seed-device.sh demo2      open Messages to Maya with "Made it home!" typed (Demo 2 start)
#
# Env: ADB (default: adb on PATH), ANDROID_SERIAL (pick a device), MAYA_NUMBER (default
# +15555215556 = a second emulator on port 5556; set a teammate's phone for S21 runs).
# On Windows run it from Git Bash. Emails and calendar events can't be seeded over adb:
# see fixtures/seed/README.md.

set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash on Windows would otherwise rewrite /sdcard/... paths

ADB="${ADB:-adb}"
MAYA_NUMBER="${MAYA_NUMBER:-+15555215556}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PHOTOS="$ROOT/fixtures/seed/photos"
REMOTE=/sdcard/Pictures

sh_dev() { "$ADB" shell "$@" | tr -d '\r'; }

require_device() {
  if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "No device found. Start the emulator or plug in the phone (adb devices)." >&2
    exit 1
  fi
}

seed_photos() {
  # "Yesterday" by the device's clock and time zone, so it matches what the agent computes.
  local day
  day="$(sh_dev 'date -d @$(( $(date +%s) - 86400 )) +%Y%m%d')"
  echo "Photos: dating them $day (yesterday on the device)"
  sh_dev "mkdir -p $REMOTE && rm -f $REMOTE/IMG_0418.png $REMOTE/IMG_0422.png"
  "$ADB" push "$PHOTOS/IMG_0418.png" "$REMOTE/IMG_0418.png" >/dev/null
  "$ADB" push "$PHOTOS/IMG_0422.png" "$REMOTE/IMG_0422.png" >/dev/null
  sh_dev "touch -t ${day}1730 $REMOTE/IMG_0418.png"   # dog in the yard, 5:30 PM
  sh_dev "touch -t ${day}1810 $REMOTE/IMG_0422.png"   # dog by the front door, 6:10 PM
  sh_dev "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null \
    || sh_dev "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$REMOTE/IMG_0418.png -d file://$REMOTE/IMG_0422.png" >/dev/null
  echo "Photos: pushed and scanned. Check: adb shell content query --uri content://media/external/images/media --projection _display_name:date_modified"
}

seed_contact() {
  if sh_dev "content query --uri content://com.android.contacts/data --projection data1 --where \"mimetype='vnd.android.cursor.item/name' AND data1='Maya'\"" | grep -q "data1=Maya"; then
    echo "Contact: Maya already exists"
    return
  fi
  if sh_dev "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n:" 2>/dev/null; then
    local id
    id="$(sh_dev "content query --uri content://com.android.contacts/raw_contacts --projection _id --sort '_id DESC'" | head -n1 | sed -n 's/.*_id=\([0-9]*\).*/\1/p')"
    if [ -n "$id" ]; then
      sh_dev "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:Maya"
      sh_dev "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$MAYA_NUMBER --bind data2:i:2"
      echo "Contact: added Maya ($MAYA_NUMBER)"
      return
    fi
  fi
  # Fallback: open a prefilled contact form. Tapping Save once is a setup step, not part of the demo.
  sh_dev "am start -a android.intent.action.INSERT -t vnd.android.cursor.dir/contact -e name Maya -e phone $MAYA_NUMBER" >/dev/null
  echo "Contact: adb couldn't write contacts directly. A prefilled 'Maya' form is open on the device: tap Save."
}

demo2_start() {
  sh_dev "am start -a android.intent.action.SENDTO -d sms:$MAYA_NUMBER --es sms_body 'Made it home!' -p com.google.android.apps.messaging" >/dev/null
  echo "Demo 2: Messages is open to Maya with 'Made it home!' typed. Check the recipient shows as Maya."
}

require_device
case "${1:-all}" in
  photos) seed_photos ;;
  contact) seed_contact ;;
  demo2) demo2_start ;;
  all) seed_photos; seed_contact ;;
  *) echo "Usage: $0 [all|photos|contact|demo2]" >&2; exit 2 ;;
esac
