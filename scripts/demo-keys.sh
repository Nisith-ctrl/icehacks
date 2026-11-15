#!/usr/bin/env bash
# Keypress controller for recording the demos: say a line out loud, press its key, and SuuS gets
# the exact same sentence (DEBUG_UTTERANCE, debug builds). Run from Git Bash. See docs/video-script.md.
# Env: ADB (default: adb on PATH).
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"

say() {
  "$ADB" shell "am broadcast -a com.icehacks.suus.DEBUG_UTTERANCE --es text \"$1\" -p com.icehacks.suus" >/dev/null 2>&1
  echo "  -> sent: $1"
}

menu() {
  cat <<'EOF'

 DEMO 1  (library workshop)
   1  "Suus, sign me up for the library workshop that fits my calendar"
   2  "Email. Remember that for event signups."
   3  "Submit"
 DEMO 1b (harvest festival)
   4  "Suus, sign me up for a volunteer shift at the harvest festival that fits my calendar"
   5  "Setup crew"
   6  "Submit"
 DEMO 2  (photo hand-off)
   0  [setup] open the Maya draft in Gmail   <- press BEFORE you start Demo 2
   q  "Suus, take over. Attach yesterday's dog photo"
   w  "The one in the yard"
   e  "Suus, undo the photo"
   r  "Suus, use the one by the door"
   t  "Suus, send it"
   y  "Send"
 ANY TIME
   s  "Stop"          h  home screen        m  show this menu      x  quit
EOF
}

menu
while true; do
  read -rsn1 -p "key> " k; echo
  case "$k" in
    1) say "Suus, sign me up for the library workshop that fits my calendar" ;;
    2) say "Email. Remember that for event signups." ;;
    3|6) say "Submit" ;;
    4) say "Suus, sign me up for a volunteer shift at the harvest festival that fits my calendar" ;;
    5) say "Setup crew" ;;
    0) ADB="$ADB" bash "$REPO/scripts/seed-device.sh" demo2 && echo "  -> Maya draft open" ;;
    q) say "Suus, take over. Attach yesterday's dog photo" ;;
    w) say "The one in the yard" ;;
    e) say "Suus, undo the photo" ;;
    r) say "Suus, use the one by the door" ;;
    t) say "Suus, send it" ;;
    y) say "Send" ;;
    s) say "Stop" ;;
    h) "$ADB" shell input keyevent KEYCODE_HOME; echo "  -> home" ;;
    m) menu ;;
    x) exit 0 ;;
    *) echo "  (unknown key, press m for the menu)" ;;
  esac
done
