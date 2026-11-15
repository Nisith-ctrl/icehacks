# Video script and voiceover (≤ 3:00)

The demo video for Devpost: what's on screen, what to say, and what we measured on recording
day (Sun Nov 15, 2026). The older beats in `docs/submission-notes.md` section 12 are superseded
by this file: the demo dates and the way commands were given changed on recording day.

## How it was recorded

- **Device:** the Android 15 emulator (`nisith_pixel_35`), signed in as the test user Alex Rivera,
  with the Nov 14 invites in Gmail and the dentist (Tue Nov 17, 10:00) and lunch (Fri Nov 20,
  13:30) in Calendar.
- **Commands were keyed, then voiced over.** OBS held the PC's microphone, and the emulator gets
  silence while another app holds it, even with OBS's own mic disabled. So each command was sent
  as text with SuuS's test hook (`scripts/demo-keys.sh`, which sends `DEBUG_UTTERANCE`) right
  after it was said, and the commands are voiced over in the edit. Everything SuuS does after
  receiving the words is real and unedited: screen reading, taps, questions, approvals, and its
  spoken replies (captured from desktop audio).
- **Say so on screen and in the write-up.** Suggested caption: "Commands keyed in for recording:
  the screen recorder held the mic. Everything SuuS does is real."

## Recording-day results (run log in the app, exported after each take)

| | Demo 1: library workshop | Demo 1b: harvest volunteer shift | Demo 2: photo hand-off |
|---|---|---|---|
| Recorded by | Nisith | Nisith | susmith |
| Touches after setup | **0** | **0** | <!-- from susmith's run log --> |
| Commands | 3 (goal, "Email. Remember that for event signups.", "Submit") | 3 (goal, "Setup crew", "Submit") | 6 planned (below) |
| Questions | 1: how to receive the confirmation | 1: which role (contact method reused, not asked) | 1 planned: which photo |
| Approvals | 1 (Submit), granted | 1 (Submit), granted | 1 planned (Send) |
| Actions | 16 (1 failed tap, recovered) | 17 (0 failed) | |
| Time, real speed | 174.7 s | 79.6 s | (guru's earlier run: 124 s) |
| Result | Intro to 3D Printing, **Wed Nov 18, 2:00–3:30 PM**; confirmation emailed | Setup crew, **Thu Nov 19, 9 AM–12 PM**; confirmation emailed | |
| Why that slot | Tue Nov 17, 10:00 clashes with the dentist | Fri Nov 20, 1–4 PM clashes with lunch | |

By hand, the same two signups took 53 and 44 touches (`docs/metrics.md`).

**Calendar:** SuuS reads the calendar directly through Android's calendar provider (the calendar
permission), so the Calendar app never opens on screen. Say it in the voiceover, or viewers won't
know the calendar was checked. Optionally cut in a two-second shot of the calendar showing the
dentist and lunch.

**The Gmail tip on take 1:** Gmail showed a tip about emoji reactions when SuuS opened the
invite. The run log shows SuuS's second tap on the email failed (the email was already open), it
read the screen again, and tapped the signup link. That re-check is the ~20 s pause. Claim only
what the log shows: it recovered from a missed tap and carried on. Don't say it "recognised the
popup and chose to ignore it", and note the tip didn't block anything.

## Script

Speed up only the waiting, and label it ("Sped up 3x: waiting on the free AI tier"). Demo 1 needs
about 3x to fit; 1b about 2.5x. Put each command on screen as a caption as it's given.

**0:00–0:20 · The problem.** On screen: the title "SuuS", then a small date picker or tiny
keyboard. Voiceover: "Meet Alex. Arthritis makes small taps and typing painful, but talking is
easy. Voice assistants can open an app, and then they stop right at the hard part: the form, the
date picker, the attachment."

**0:20–0:30 · Say the goal.** On screen: the home screen, the robot appears. Voiceover: "SuuS is an
Android app that does the tapping for you. Alex just says what they want."

**0:30–1:20 · Demo 1, library workshop (sped up ~3x).** Caption: *"Suus, sign me up for the
library workshop that fits my calendar."* On screen: SuuS opens Gmail and the invite, taps the
link, fills the form; it asks how to send the confirmation; caption *"Email. Remember that for
event signups."*; it reads everything back; caption *"Submit."*; the confirmation page.
Voiceover: "SuuS finds the invite in Gmail and opens the signup link. It checks Alex's calendar in
the background, sees that Tuesday's session clashes with the dentist, and picks Wednesday at two.
Code fills the form, not guesses from the AI. When it needs something it doesn't know, it asks,
and Alex tells it to remember the answer. It reads everything back, and nothing is submitted until
Alex says 'Submit'."

If the Gmail tip is in the take, optional line: "Gmail threw up a tip we'd never seen. SuuS's tap
missed, it looked at the screen again, and carried on. It works from what's on screen at every
step, so surprises like this don't break it."

**1:20–1:50 · Demo 1b, a form it hasn't seen (sped up ~2.5x).** Caption: *"Suus, sign me up for a
volunteer shift at the harvest festival that fits my calendar."* On screen: a different form;
"Email" ticked without a question; the role question; caption *"Setup crew."*; read-back;
caption *"Submit."* Voiceover: "A different form, with different labels. Friday's shift clashes
with lunch, so it picks Thursday morning. It already knows Alex wants email, so the only question
is which role. A new approval, and Alex is signed up. Eighty seconds, zero touches."

**1:50–2:35 · Demo 2, hand off a half-written email (susmith).** Start: the draft "Made it home!"
to Maya open in Gmail (`scripts/demo-keys.sh` key 0, or `scripts/seed-device.sh demo2`). Captions
in order: *"Suus, take over. Attach yesterday's dog photo."* → *"The one in the yard."* → *"Suus,
undo the photo."* → *"Suus, use the one by the door."* → *"Suus, send it."* → *"Send."* Label: "To
a test address." Voiceover: "Alex can also hand off something already started. SuuS finds
yesterday's photos and asks which one. Wrong photo? 'Undo the photo' removes just the photo and
leaves Alex's words alone. Then the right one, a read-back, and nothing goes until Alex says
'Send'."

**2:35–2:50 · How it works.** On screen: the website's In depth flowchart, or the architecture
diagram. Voiceover: "Under the hood, an accessibility service reads the screen, an AI model picks
one step at a time, and code does the exact parts. Submit and Send are locked to the screen Alex
approved, and 'Stop' cancels in about a tenth of a second."

**2:50–3:00 · Close.** On screen: results card, "0 touches · 53 and 44 by hand · Android, tested
on the emulator", the website URL. Voiceover: "Two signups and an email, zero touches. SuuS: say
what you need."

## Doing a take

1. **Silence the emulator** so the listening beep doesn't end up in the recording:
   `adb shell cmd audio set-ringer-mode SILENT` (SuuS's voice is on the media and accessibility
   streams and still plays).
2. **Reset between takes.** Gmail reopens on the last email viewed, and SuuS will act on whatever
   is on screen. Before each take: `adb shell am force-stop com.google.android.gm`, the same for
   `com.android.chrome`, press Home, and clear the run log:
   `adb shell am broadcast -a com.icehacks.suus.DEBUG_RUNLOG --es op clear -p com.icehacks.suus`.
3. **Run `scripts/demo-keys.sh`** in Git Bash, keep the terminal outside the capture area, and
   click into it so keys reach it. Press each key right after saying the line, and wait for SuuS
   to finish speaking before the next one. A question waits 15 s for an answer, then asks once
   more.
4. **Export the run log** after the take:
   `adb shell am broadcast -a com.icehacks.suus.DEBUG_RUNLOG --es op export -p com.icehacks.suus`,
   then pull `/sdcard/Download/suus/runs/`.

If you want SuuS to hear a live voice instead: start the emulator with `-allow-host-audio`, turn
on Extended controls → Microphone → "Virtual microphone uses host audio input", and close every
app that uses the microphone (including OBS).
