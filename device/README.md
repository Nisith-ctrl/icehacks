# device (T01)

Screen reading and validated actions for the agent. No business logic: it doesn't know about
forms, emails or photos.

- `DeviceScreenController`: the `ScreenController` everyone uses. For every action it takes a
  fresh snapshot, refuses apps outside `AllowedApps`, re-finds the target by `NodeSignature`
  (centre may move up to 24 px), refuses commit taps (`Tap(isCommit = true)`) unless
  `approval.fingerprint == Sha256Fingerprinter.of(fresh snapshot)`, acts, waits for the screen to
  settle (≥150 ms, then 300 ms without events, max 5 s), and returns the new snapshot.
  `EnterText` is verified: the text must be in an editable node at the same place afterwards.
  Three actions in a row that don't change the fingerprint return `Failed("no progress")`.
- `AccessibilityBackend`: reads only `TYPE_APPLICATION` windows, so the overlay, IME and system UI
  are never in snapshots or fingerprints. Tap = `ACTION_CLICK` on the node or nearest clickable
  ancestor, else a gesture tap at the bounds centre. Scroll = `ACTION_SCROLL_FORWARD/BACKWARD`
  (DOWN = forward), else a swipe.
- Logcat tag: `SUUS-Device`.

## adb debug hooks (debug builds, service on)

```bash
# Snapshot -> /sdcard/Download/suus/<name>.json and Logcat
adb shell am broadcast -a com.icehacks.suus.DEBUG_SNAPSHOT --es name home-screen
adb pull /sdcard/Download/suus/home-screen.json fixtures/screens/

# Actions. Node ids refer to the last snapshot taken by the hooks.
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es tapText Gmail
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es tap n12
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es node n7 --es text "Ada Lovelace"
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es scroll down
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es back 1
adb shell am broadcast -a com.icehacks.suus.DEBUG_ACTION --es launch com.google.android.gm
# Raw DeviceAction JSON (SuusJson, "type" discriminator):
adb shell "am broadcast -a com.icehacks.suus.DEBUG_ACTION --es json '{\"type\":\"Tap\",\"nodeId\":\"n5\"}'"

adb logcat -s SUUS-Device
```

Turn the service on without touching the device:

```bash
adb shell settings put secure enabled_accessibility_services com.icehacks.suus/.SuusAccessibilityService
adb shell settings put secure accessibility_enabled 1
```
