---
date: 2026-11-12
author: Nisith
task: all
status: accepted
---

# We test and record on the emulator only (no physical phone)

## Decision

- All remaining testing and the demo recordings use the Android emulator (Pixel, API 35, Google
  Play image). We don't use the Galaxy S21 or any other physical phone.
- Every "on the S21" step in the tasks and assignments now means **on the emulator**: real-speech
  wake checks use the laptop microphone (start the emulator with `-allow-host-audio`; the ▶ button
  in Android Studio starts it without the mic), and overlay readability is checked on the emulator.
- Docs, the video and Devpost say plainly that everything was tested on the emulator.

## What this means for Demo 2 (photo message)

The emulator has no MMS, so Google Messages refuses photo attachments
([2026-11-11-emulator-no-mms](../dead-ends/2026-11-11-emulator-no-mms.md)), and it hides its text box from accessibility
([2026-11-09-messages-compose-a11y-gaps](../dead-ends/2026-11-09-messages-compose-a11y-gaps.md)). On the emulator, Demo 2 in Messages can only go up to
the attach step.

Options, decided by Nisith:
1. **Gmail draft instead of Messages (proposed).** Same story: Alex has started an email to Maya
   ("Made it home!"), says "Take over. Attach yesterday's dog photo", "Undo the photo", "Use the
   one by the door", then "Send" after a read-back. Gmail attachments work on the emulator and
   the send goes to a test address. Needs agent work from Susmith.
2. Keep Messages, show it up to the attach step, label it a controlled test, and explain the
   emulator limit. The selective-undo demo can't be shown.

## Why

Nobody on the team has the S21 available. One device type makes testing and recording simpler.

## Affects

`assignments` (private notes), `build-plan` (private notes), [T01](../README.md#task-board), [T02](../README.md#task-board), [T03](../README.md#task-board)
(Demo 2), [T05](../README.md#task-board) (M4 "S21 checks" become emulator checks), `resources` (private notes).
