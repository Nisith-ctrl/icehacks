# Metrics: hands-free vs by hand

For each demo, the touches and spoken commands needed **after setup**. Waking SuuS counts as a
spoken command; setup doesn't count. Agent numbers come from SuuS's own run log
(`DEBUG_RUNLOG --es op export`, sessions CSV), which counts every click the phone reports while
SuuS isn't acting.

## Agent runs (emulator, 2026-11-11; photo hand-off 2026-11-13)

| Demo | Touches | Spoken commands | Questions asked | Approvals | Actions by SuuS | Time | Result |
|---|---|---|---|---|---|---|---|
| Library workshop registration | **0** | 2 ("Suus, sign me up for the library workshop that fits my calendar", "Submit") | 0 (Email preference already saved) | 1 | 12 | 85 s | Confirmed LIB-282497, 5 of 5 values verified |
| Volunteer shift signup | **0** | 3 (request, "Setup crew", "Submit") | 1 (role) | 1 | 17 | 101 s | Confirmed VOL-730060, 6 of 6 values verified |
| Photo hand-off + undo (Gmail draft, 2026-11-13) | **0** | 6 ("Suus, take over. Attach yesterday's dog photo", "The one in the yard", "Suus, undo the photo", "Suus, use the one by the door", "Suus, send it", "Send") | 1 (which photo) | 1 | 8, plus 1 undo | 124 s | Sent to maya@example.com; undo removed only the photo, the rest of the draft verified unchanged |

Notes:
- Spoken commands were injected as text (`DEBUG_UTTERANCE`) on the emulator. Real speech was tested separately on the emulator with the laptop microphone. We
  test only on the emulator (team decision, Nov 12).
- The library run reused the "Email for event signups" preference saved in an earlier run. On a
  fresh device it asks once ("How should they confirm?"), which adds 1 question and 1 spoken command.
- Time includes waiting on the free-tier rate limit (a few 1 to 17 s pauses per run).
- Run log sessions 25 and 26 in `suus-sessions-20261111-113432.csv`. (Session 26 showed 4 touches
  in that export: they were taps made about 50 minutes after the run ended. PR 28, now merged,
  counts only touches within 60 s of a run. Clearing the log before each recorded run still keeps
  the export easy to read.)
- Photo hand-off: run log sessions 82–85 in `suus-sessions-20261113-104439.csv` (guru, after PRs
  35, 37 and 38). It ran in a Gmail draft because the emulator has no MMS for Messages (vault decision
  2026-11-13-demo2-gmail-draft). About 60 s of the 124 s was free-tier rate-limit waiting (pauses of
  1–16 s). The time doesn't include the pauses between commands, where a person would be talking.
  An earlier test the same morning (PR 35) ended with "Cancel" at the read-back and sent nothing.

## Manual runs (emulator, 2026-11-12, by Nisith)

The same tasks done by hand on the emulator, from the home screen to the confirmation page.
Every touch was counted with `adb shell getevent` (each new touch on the screen = 1). Typing used
the on-screen keyboard, so every key press counts; scrolling was one drag per swipe (no mouse
wheel). Same details as SuuS used (name, email, phone), and the person already knew the tasks.

| Demo | Touches | Time |
|---|---|---|
| Library workshop registration | **53** | 2:21 (141 s) |
| Volunteer shift signup | **44** | 1:24 (84 s) |
| Photo hand-off + undo (Gmail draft) | not measured yet | |

## Hands-free vs by hand

| Demo | By hand | With SuuS |
|---|---|---|
| Library workshop registration | 53 touches, 141 s | **0 touches**, 2 spoken commands, 85 s |
| Volunteer shift signup | 44 touches, 84 s | **0 touches**, 3 spoken commands, 101 s |

What this shows, stated plainly:
- The saving is in **touches**: 97 precise taps across the two tasks by hand, none with SuuS.
  For someone with arthritis, each of those taps (most of them tiny keyboard keys) is the hard part.
- Time is similar, not faster: SuuS was quicker on the library task and slower on the volunteer
  task. Its time includes waiting on the free AI tier's rate limit, and the person doing it by
  hand has no motor difficulty and already knew the steps.

For reference, the by-hand path includes opening Gmail, finding and opening the invite, opening
the link, switching to Calendar to check both dates, switching back, choosing the session, typing
name, email and phone, choosing the confirmation method and tapping Register. SuuS did the same
work in 12 actions with no touches.
