---
date: 2026-11-09
author: Susmith (T01 agent)
task: T01, T03 (Demo 2)
---

# Google Messages (emulator build) hides its compose box and photo panels from accessibility

## What we tried

Driving Demo 2 on `stg_pixel_35` with Google Messages
`messages.android_20240528_00_RC01` (the version that ships on the API 35 Play image): read the
draft, open the attach panel, pick a dog photo.

## What happened

- Visible to accessibility (our snapshot and `uiautomator dump --windows`): Back, Maya, Call, More,
  "Show more options", "Show attach content screen" (+), "Show attach media screen" (gallery),
  emoji, and Send ("Send SMS" is a child of the clickable `Compose:Draft:Send` node).
- **Not visible**: the message text box ("Made it home!"), even when focused with the keyboard
  up; the attach-media photo grid; and the "+" options panel (Gallery, GIFs, Files, Location…).
  They look like classic views inside the Compose screen that aren't linked into the
  accessibility tree. There's only one app window, so it isn't a window-filtering problem on our side.
- Result: the agent can open the panels but can't see or tap a photo without hard-coded screen
  coordinates (the plan says no). It also can't read the draft text, so undo can't confirm that
  "Made it home!" survived.

## Do instead

1. Sign in the test account, **update Google Messages from the Play Store**, and check again (a
   `DEBUG_SNAPSHOT` with the gallery open should list the thumbnails). Newer builds may fix it.
2. **guru: check the S21's messaging app** (Samsung Messages or Google Messages) with
   `DEBUG_SNAPSHOT` in a draft and with the photo panel open.
3. If both fail: the plan's fallback, "a route that preserves the active draft", e.g. the system
   photo picker (`com.google.android.providers.media.module`, which is well exposed), if Messages
   offers it. As a last resort, a labelled controlled-test run.

Snapshot: `fixtures/screens/messages-draft.json`.
