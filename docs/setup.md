# Setting up SuuS

SuuS is a voice agent for Android. You say what you want ("Suus, sign me up for the library
workshop that fits my calendar"), and it does the taps in Gmail, Calendar, Chrome and Messages
for you. It always reads back and waits for you to say **Submit** or **Send** before anything goes out.

## For a user: one-time setup on the phone

1. Install the app (`app-debug.apk` from a build, see below) and open **SuuS**.
2. The setup screen walks through each step in order, with one large button per step:
   1. **Turn on SuuS**: opens Accessibility settings. Choose SuuS (it may be under Installed apps)
      and switch it on. This is what lets SuuS read the screen and tap.
   2. **Allow the microphone** ("While using the app"), so it hears "Suus" and your answers.
   3. **Allow the calendar** (read only), so it picks times that don't clash.
   4. **Allow photos**, so it can find "yesterday's dog photo".
   5. **Allow notifications**.
   6. **Tell SuuS about you**: name, email and phone it may fill into forms. Anything missing, it asks for by voice.
   7. **Check that it hears you**: say "Suus" and the robot pops up listening.
3. Done. From then on: say **"Suus"**, then the request. Say **"Stop"** at any time to cancel.

Setup taps don't count toward the demo metrics; everything after setup is hands-free.

## For a developer: build and install

```bash
# Groq key (free tier) goes in local.properties, never in the repo:
#   GROQ_API_KEY=...
JAVA_HOME="<Android Studio>/jbr" ./gradlew test :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Turn the accessibility service on without touching the device (Git Bash: prefix with
`MSYS_NO_PATHCONV=1` so the `/` survives):

```bash
adb shell settings put secure enabled_accessibility_services com.icehacks.suus/com.icehacks.suus.SuusAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Reinstalling turns the service off; run the two lines again and wait about 10 s.

Debug builds accept adb broadcasts (see `app/.../DebugReceiver.kt`):

| Broadcast | What it does |
|---|---|
| `DEBUG_UTTERANCE --es text "'Suus ...'"` | Acts as if you said it (keep the inner quotes) |
| `DEBUG_SNAPSHOT --es name X` | Writes the screen as SuuS sees it to `/sdcard/Download/suus/X.json` |
| `DEBUG_RUNLOG --es op export\|clear\|summary` | Exports the run log to `/sdcard/Download/suus/runs/` |
| `DEBUG_OVERLAY --es demo point\|caption\|highlight` | Shows one overlay feature |

All take `-a com.icehacks.suus.<NAME> -p com.icehacks.suus`.

## Seeding a device for the demos

Full details: `fixtures/seed/README.md`. Short version, on the recording day:

1. A human signs the test Google account in on the device and gets Chrome past its first-run screen.
2. From the form site's `/seed/` page: send both invite emails to the test account and import
   `stg-conflicts.ics` into its calendar (dentist clashes with library session 1, lunch with volunteer shift B).
3. `scripts/seed-device.sh`: photos dated yesterday, and the Maya contact.
4. Before the photo demo: `scripts/seed-device.sh demo2` opens a Gmail draft to maya@example.com, "Made it home!". (Messages can't attach photos on the emulator: no MMS.)
5. Clear saved preferences before the library demo so the "remember Email" step happens fresh.

Test forms: https://saythegoal-forms-nisith.vercel.app (library and volunteer, plus their confirmation pages).
