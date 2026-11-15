---
date: 2026-11-13
author: guru
task: T03 (Demo 2 in Gmail)
---

# Gmail: "Insert photo" isn't an attachment, and removed attachments linger in the a11y tree

## What we tried

Demo 2 in a Gmail draft on the emulator (API 35), letting the model pick from Gmail's paperclip menu.

## What happened

1. **"Insert photo" puts the image inside the email body.** The menu has *Attach file*,
   *Insert from Drive* and *Insert photo*. The model picks "Insert photo" for a photo almost every
   time. It inserted the picture inline twice, with no "Remove attachment" control, so undo couldn't
   find it. Fixed in code (PR #35): that tap is redirected to "Attach file".
2. **The attachment card appears late.** After the Files picker closes, Gmail shows the card a
   moment later, so the snapshot right after the pick tap doesn't have it yet. Fixed: the next
   turn's screen records it.
3. **Stale nodes after removal.** After tapping "Remove attachment IMG_0418.png", the tile is gone
   on screen but stays in the cached accessibility tree with `isVisibleToUser = true`, so undo said
   "still seems to be there". Fixed: each capture calls `AccessibilityService.clearCache()`
   (API 34+). Snapshots stay under a second.
4. `SENDTO mailto:` ignores `EXTRA_SUBJECT`/`EXTRA_TEXT`; put them in the link
   (`?subject=…&body=…`). A `+alias` of the test account shows as "Alex Rivera" on the To chip,
   so Maya is `maya@example.com`.

## Do instead

Attach in Gmail via **Attach file → Files picker → tap the file name**. Gives a real attachment
with `contentDescription = "Remove attachment <name>"`, which `DraftLedger` (agent/DraftLedger.kt) already understands.
