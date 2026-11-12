# Tested environments

## Devices

| Device | Android | Status |
|---|---|---|
| Emulator `stg_pixel_35` (Pixel 7 profile, Google Play image, x86_64) | 15 (API 35), build `AE3A.240806.036` | Library and volunteer demos pass end to end, hands-free (2026-11-11) |

## App versions on the emulator

| App | Package | Version |
|---|---|---|
| Gmail | `com.google.android.gm` | 2024.04.14.625469418.Release |
| Google Calendar | `com.google.android.calendar` | 2024.13.0-620698557-release |
| Chrome | `com.android.chrome` | 124.0.6367.219 |
| Google Messages | `com.google.android.apps.messaging` | messages.android_20261027_00_RC00 (updated from Play on 2026-11-11) |
| Photo picker | `com.google.android.providers.media.module` | 15 |

Planner: Groq free tier, model `openai/gpt-oss-120b` (see `vault/team-memory/api-notes/groq-api.md`).
Speech: Android's `SpeechRecognizer` (the emulator has no on-device en-US model, so it uses the standard one).

## What SuuS can act on

SuuS only acts inside an allowlist: the launcher, Gmail, Calendar, Chrome, Messages, the
system photo picker and the file chooser. Anywhere else it stops. Forms are filled only on the test form site
(`saythegoal-forms-nisith.vercel.app`, plus `10.0.2.2` / `localhost` for local runs).

| Workflow | Status |
|---|---|
| Find an invite in Gmail, open its link, pick the session that fits the calendar, fill the form, read back, submit on "Submit", verify the confirmation page | Works (library: LIB-282497; volunteer: VOL-730060) |
| Remember an answer ("use Email for event signups") and reuse it on the next form; "What do you remember?", "Forget …" | Works |
| Take over a Messages draft, attach yesterday's photo, "Undo the photo", "Use the one by the door", read back, "Send" | In progress (see below) |
| "Stop" at any time | Cancels the running action in about 3 ms and says "Stopped." about 120 ms after the word arrives |

### Form controls

Text fields, radio buttons, checkboxes and dropdowns (`<select>`, which opens Chrome's native list),
including fields below the fold (it scrolls). Values are typed with the accessibility API, so no
keyboard is used. Submit and Send are only tapped after a spoken approval whose screen
fingerprint still matches; if anything on screen changed after you approved, it asks again.

## Controlled tests and limits

- Demo runs on the emulator were driven with `DEBUG_UTTERANCE` (the same text the recognizer would
  produce) so runs are repeatable. Real-speech wake-up was tested on the emulator with the laptop microphone
  (start it with `-allow-host-audio`). We test only on the emulator; no physical phone was used.
- The two forms and the invite emails are test fixtures we built (`fixtures/forms`); no real
  registration happens.
- Messages: the May 2024 build hid its text box and photo grid from accessibility, so the photo demo
  couldn't see the draft. It was updated from the Play Store on 2026-11-11 and is being re-checked.
  The emulator has no MMS, so Messages refuses photo attachments ("Attachments are not supported in
  this conversation") and the photo demo stops cleanly before attaching, leaving the draft as it was.
- Free-tier rate limits (8K tokens per minute, 200K per day) slow some turns; SuuS waits and retries.
  Don't rehearse many runs back to back before recording.
