# app: the Android app

Builds the APK. It hosts the accessibility service that runs everything, and the app's own screens.

| File | What it does |
|---|---|
| `SuusAccessibilityService.kt` | Hosts everything: builds the device controller, voice, agent and overlay through their factories, then sends voice commands to the agent one at a time. Stop cancels the running command and drops anything queued. |
| `SetupActivity.kt` | The home screen and setup checklist: one card per step with a status chip and one big button. |
| `ui/OnboardingActivity.kt` | First-run setup, one page at a time: welcome, permissions, About you, how SuuS should talk. |
| `ui/ProfileActivity.kt` | "About you": the name, email and phone SuuS puts into forms. |
| `ui/CustomizeActivity.kt`, `ui/Customize.kt` | "Make SuuS yours": tone, how much it explains, speech rate, robot size, colours. Applied the moment they're picked. |
| `ui/MemoryActivity.kt`, `ui/MemoryStore.kt` | "What I remember": every saved preference in plain words, each with a big Remove button. |
| `ui/Ui.kt` | The shared look, built for people who can't tap precisely: full-width buttons at least 64 dp tall, text 16 sp or larger, nothing timed or swipe-only. |
| `DebugReceiver.kt` | Debug-build adb hooks for screen snapshots and actions (see [`device/`](../device/)). |

Resources: colours for light and dark mode in `res/values(-night)/colors.xml`, the accessibility
service config in `res/xml/`, and the bundled fonts (Atkinson Hyperlegible, Pixelify Sans, with
their licences) in `assets/fonts/`.

```bash
./gradlew :app:installDebug      # debug build, needs GROQ_API_KEY in local.properties
./gradlew :app:assembleRelease   # release build: calls the relay (SUUS_RELAY_URL / SUUS_RELAY_TOKEN),
                                 # signed if SUUS_KEYSTORE is set (see app/build.gradle.kts)
```

Full setup: [docs/setup.md](../docs/setup.md).
