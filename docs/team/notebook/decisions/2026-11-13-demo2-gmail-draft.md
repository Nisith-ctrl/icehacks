---
date: 2026-11-13
author: guru
task: T03, T04
status: accepted
---

# Demo 2 is a Gmail draft, not Messages

## Decision

- Demo 2 (photo hand-off) runs in a **Gmail draft** on the emulator. This is option 1 from
  [2026-11-12-emulator-only](2026-11-12-emulator-only.md). guru chose it on Nov 13 because Nisith hadn't picked yet and the
  freeze is tonight.
- The story is unchanged. A draft to Maya, subject and body "Made it home!", is open. Then:
  "Suus, take over. Attach yesterday's dog photo" → "the one in the yard" → "Suus, undo the
  photo" → "Suus, use the one by the door" → "Suus, send it" → read-back → "Send".
- Start it with `scripts/seed-device.sh demo2`.
- Maya's address is **`maya@example.com`**, a reserved test domain. The sent email reaches nobody
  and bounces back to the test inbox. Gmail shows the address on the To chip (it doesn't use the
  device contact), which is fine for the video.
- The Messages flow (PR #30) stays in the code, and `seed-device.sh demo2-messages` still opens it.
  On the emulator it stops at the attach step.

## Why

The emulator has no MMS ([2026-11-11-emulator-no-mms](../dead-ends/2026-11-11-emulator-no-mms.md)), and its Messages build hides the draft
text box ([2026-11-09-messages-compose-a11y-gaps](../dead-ends/2026-11-09-messages-compose-a11y-gaps.md)). Gmail attachments work on the emulator, and
the undo can confirm that the text was left alone.

## Affects

[T03](../README.md#task-board) (Demo 2 flow, PR #35), [T04](../README.md#task-board) (video script,
`docs/devpost.md`, `docs/tested-environments.md` still say Messages), `build-plan` (private notes) (IC5).
