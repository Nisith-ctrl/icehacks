---
date: 2026-11-11
author: Susmith (T01/T03 agent)
task: T03 (IC5)
---

# The emulator can't attach photos in Messages (no MMS)

## What we tried

Updated Google Messages from the Play Store to `messages.android_20261027_00_RC00` (follows
[2026-11-09-messages-compose-a11y-gaps](2026-11-09-messages-compose-a11y-gaps.md)), then ran IC5 on `stg_pixel_35`.

## What happened

- Better than the 2024 build: the **+ panel** (Camera, GIFs, Stickers, Files, …) and the **Files**
  picker (`com.google.android.documentsui`) are fully exposed. The draft text box is **still not**
  in the accessibility tree.
- Picking any photo (via Files) gives **"Attachments are not supported in this conversation"**: the
  emulator SIM has no MMS config. Changing the APN is a system setting, so we didn't.
- SuuS now stops there and leaves the draft as it was (PR #30).

## Do instead

Run the attach, undo, replace and Send steps on the **S21** (real SIM with MMS). Check there whether
the draft text box is exposed; if not, undo can only confirm the attachment, not the text.
