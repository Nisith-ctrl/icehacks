# overlay (T04 M2): the character

`createOverlay(service)` returns `CharacterOverlay` (falls back to `FakeOverlay` if the window
can't be created).

- One full-screen `TYPE_ACCESSIBILITY_OVERLAY` window, `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE |
  FLAG_LAYOUT_IN_SCREEN`: user taps, our gesture taps and the keyboard pass through. Window title
  `SuusOverlay`. The device layer reads only `TYPE_APPLICATION` windows and ignores events
  from our package, so the overlay is never in snapshots or fingerprints (checked on the emulator).
- `pointAt(bounds, caption)`: the character glides (420 ms) to the side of the target with the most
  room, never covering it; it points and looks at the target, and an amber ring fades in. Returns
  in ≤ 650 ms. `showResult` shows a green tick or red cross for 380 ms, then clears the ring.
- The character is guru's **SuuS robot sprite** (`res/drawable-nodpi/suus_walk_0..7.png`, cut from
  the design sheet). It is 88 dp tall, walks through its 8 frames while it glides to a target (and
  faces the way it walks), then stands still.
- `setMode`: a round **badge by the robot's head**, with a colour **and** a symbol per mode, so it
  reads without colour vision: listening = green, moving sound bars, plus a pulse behind the robot;
  thinking = amber, three dots lifting in turn, and the robot bobs; acting = blue "tap" dot and a
  pointer toward the target; waiting for approval = purple "?"; problem = red "!"; idle = no badge;
  hidden = nothing.
- `highlight(list)`: cyan rings for the read-back; `highlight(emptyList())` clears them.
- Captions: guru's **pixel speech bubble** (cream fill, mint inner line, dark green outline, stepped
  corners, a stepped tail toward the robot), 22 sp bold dark green text, up to 3 lines, inside the
  system-bar insets. They follow the phone's font size setting up to 1.3x. They go to whichever end of the screen covers the focused target, the highlights and the
  character least (`OverlayLayout.captionAtTop`).
- Agent usage: `setMode(ACTING)` → `pointAt(node.bounds, "Filling in your email")` →
  `screen.perform(...)` → `showResult(node.bounds, ok)`.

## adb demo (debug builds, service on)

```bash
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo point --es text Gmail
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo point --ei l 100 --ei t 900 --ei r 500 --ei b 1100
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo caption --es text "Filling in your email"
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo highlight --es text "Gmail,Chrome"
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo mode --es mode LISTENING
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo tour
adb shell am broadcast -a com.icehacks.suus.DEBUG_OVERLAY --es demo clear
```

Logcat tag `SUUS-Overlay`. Placement geometry is unit-tested in `OverlayLayoutTest`.
