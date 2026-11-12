# Metrics: hands-free vs by hand

For each demo, the touches and spoken commands needed **after setup**. Waking SuuS counts as a
spoken command; setup doesn't count. Agent numbers come from SuuS's own run log
(`DEBUG_RUNLOG --es op export`, sessions CSV), which counts every click the phone reports while
SuuS isn't acting.

## Agent runs (emulator, 2026-11-11)

| Demo | Touches | Spoken commands | Questions asked | Approvals | Actions by SuuS | Time | Result |
|---|---|---|---|---|---|---|---|
| Library workshop registration | **0** | 2 ("Suus, sign me up for the library workshop that fits my calendar", "Submit") | 0 (Email preference already saved) | 1 | 12 | 85 s | Confirmed LIB-282497, 5 of 5 values verified |
| Volunteer shift signup | **0** | 3 (request, "Setup crew", "Submit") | 1 (role) | 1 | 17 | 101 s | Confirmed VOL-730060, 6 of 6 values verified |
| Photo message takeover + undo | | | | | | | Not run yet |

Notes:
- Spoken commands were injected as text (`DEBUG_UTTERANCE`) on the emulator. Real speech was tested separately on the emulator with the laptop microphone. We
  test only on the emulator (team decision, Nov 12).
- The library run reused the "Email for event signups" preference saved in an earlier run. On a
  fresh device it asks once ("How should they confirm?"), which adds 1 question and 1 spoken command.
- Time includes waiting on the free-tier rate limit (a few 1 to 17 s pauses per run).
- Run log sessions 25 and 26 in `suus-sessions-20261111-113432.csv`. (Session 26 showed 4 touches
  in that export: they were taps made about 50 minutes after the run ended. PR 28 limits the
  count to touches within 60 s of a run; until it's merged, clear the log before each run.)

## Manual runs (to measure)

Record the same tasks done by hand with **Developer options → Show taps** on, and count every tap
(including each keyboard key) from the home screen to the confirmation page.

| Demo | Touches | Time |
|---|---|---|
| Library workshop registration | | |
| Volunteer shift signup | | |
| Photo message takeover + undo | | |

For reference, the by-hand path includes opening Gmail, finding and opening the invite, opening
the link, switching to Calendar to check both dates, switching back, choosing the session, typing
name, email and phone, choosing the confirmation method and tapping Register. SuuS did the same
work in 12 actions with no touches.
