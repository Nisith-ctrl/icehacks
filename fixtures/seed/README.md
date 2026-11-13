# Demo seed data

Everything the three demo runs need on the device and in the test Google account. Dates are
relative to the **seed day** (normally the recording day), so re-seed right before recording.

## Test persona (the agent's "known details")

These match `Profile` in `agent/.../AgentConfig.kt`. Change both together.

| Field | Value |
|---|---|
| Full name | Alex Rivera |
| Email | alex.rivera.test@example.com |
| Phone | 555-0142 |

Not in the profile on purpose (the agent must ask): library **confirmation method**
(Demo 1 question, saved as a preference), volunteer **preferred role** (new required field).

## Accounts (a human sets these up)

Agents never create accounts or type passwords.

- **User test account**: a Google account signed in on the emulator and the S21 (Gmail, Calendar,
  Chrome). Get Chrome past its first-run screen once by hand.
- **Sender test account**: any second email account, used only to send the two invites.
- Record which account is signed in on which device in `vault/team-memory/api-notes/devices.md`
  (address only, never the password).

## 1. Invite emails

Open the seed page on the deployed form site: `<form site>/seed/` (locally:
`http://localhost:8080/seed/`). It shows the exact subject and body for both invites with the
dates and links filled in. Send both from the sender account to the user account.

What they contain (seed day = D):

| Invite | Subject | Options in the body | Link |
|---|---|---|---|
| Library | Invitation: Intro to 3D Printing Workshop at Riverside Library | Session 1: D+3, 10:00 to 11:30 AM. Session 2: D+4, 2:00 to 3:30 PM | `/library/?s1=…&s2=…&d=90` |
| Volunteer | Volunteers needed: Riverside Harvest Festival | Shift A: D+5, 9:00 AM to 12:00 PM. Shift B: D+6, 1:00 to 4:00 PM | `/volunteer/?s1=…&s2=…&d=180` |

The link carries the times, so the form always shows the same options as the email it came from.
Delete old invites before re-seeding so the agent only finds one of each.

## 2. Calendar conflicts

The seed page has a **Download stg-conflicts.ics** button. Import it into the user account's
Google Calendar (calendar.google.com → Settings → Import & export → Import), or create by hand:

| Event | When | Blocks |
|---|---|---|
| Dentist appointment | D+3, 10:00 to 11:00 AM | Library Session 1, so the agent must pick **Session 2** |
| Lunch with Sam | D+6, 1:30 to 2:30 PM | Volunteer Shift B, so the agent must pick **Shift A** |

The device's time zone must match the account's calendar time zone. Open Calendar on the device
once after importing so it syncs, and delete the old events when you re-seed.

## 3. Photos, contact and the Demo 2 draft (adb)

```bash
scripts/seed-device.sh            # photos + Maya contact
scripts/seed-device.sh demo2      # right before Demo 2: a Gmail draft to maya@example.com, "Made it home!"
scripts/seed-device.sh demo2-messages   # the same in Messages (emulator: only up to attach, no MMS)
```

- `photos/IMG_0418.png`: dog in a yard (fence, tree, ball), dated **yesterday 5:30 PM**.
- `photos/IMG_0422.png`: dog sitting by a blue front door, dated **yesterday 6:10 PM**.
  This is "the one by the door". File names are neutral so the agent has to look at the image.
- Regenerate with `python scripts/make_seed_photos.py`, or replace them with real photos under
  the same names.
- **Maya**: Demo 2 emails `MAYA_EMAIL` (default `maya@example.com`, a reserved test domain: the
  email reaches nobody and bounces back to the test inbox). The Messages variant uses a contact with `MAYA_NUMBER` (default `+15555215556`, which is a second emulator running
  on port 5556). Only send to test addresses and numbers.

## Before each take

1. Seed day correct? If not: new emails, new calendar import.
2. `scripts/seed-device.sh` (re-dates the photos to yesterday).
3. Demo 1/2 forms: nothing to reset. Each submission is independent.
4. Demo 2: `scripts/seed-device.sh demo2`, then check the draft and recipient on screen and hide the keyboard.
5. Clear the agent's saved preferences before Demo 1 so the "remember" step happens fresh.
