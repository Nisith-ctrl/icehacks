# scripts

Helpers for setting up the emulator and recording the demos. Run them from Git Bash on Windows or
any shell on macOS and Linux. Set `ADB` if `adb` isn't on your PATH.

| Script | What it does |
|---|---|
| `seed-device.sh` | Seeds the emulator or phone for the demos: pushes the two dog photos (dated yesterday), adds the Maya contact, or opens the Demo 2 Gmail draft (`seed-device.sh demo2`). Safe to re-run before every take. |
| `demo-keys.sh` | Keypress controller for recording: each key sends one demo line to SuuS as if it were spoken (debug builds). Used when a screen recorder holds the microphone. See [docs/hackathon/video-script.md](../docs/hackathon/video-script.md). |
| `make_seed_photos.py` | Draws the two seeded dog photos in `fixtures/seed/photos/`. Standard library only. |

Emails and calendar events can't be added over adb; see [fixtures/seed/README.md](../fixtures/seed/README.md).
