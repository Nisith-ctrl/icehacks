# agent: planning, forms, memory and the run log

Turns a spoken goal into actions. The model chooses one next step at a time; code does everything
that has to be exact.

## The loop

| File | What it does |
|---|---|
| `SuusAgent.kt` | Routes each command. Goals run the `AgentLoop`. "Undo …" removes exactly one of the agent's own draft edits, with no model call, and checks nothing else changed. Memory commands list or forget saved preferences. |
| `AgentLoop.kt` | One goal, one model-chosen action at a time. Code checks every choice, points the robot at the target, acts through the device layer, records draft edits, and enforces the guards: allowed apps, the test form host, an action cap, no-progress detection, and spoken approval before any commit. |
| `Planner.kt`, `GroqPlanner.kt` | What the model sees each turn (goal, notes, recent steps, the screen as text) and the Groq client. Each turn is one stateless request that returns one tool call. |
| `AgentPrompt.kt`, `AgentTools.kt` | The system prompt and the tool definitions given to the model. |
| `ScreenText.kt` | The compact text view of a screen: one line per useful node, e.g. `n20 Button "Submit" [tap]`. |
| `AgentFactory.kt`, `AgentConfig.kt` | Builds the agent. Debug builds call Groq with `GROQ_API_KEY` from `local.properties`; release builds call the [relay](../relay/), so the APK never holds a key. |

## Exact parts, in code

| File | What it does |
|---|---|
| `form/FormReader.kt`, `FormMapper.kt`, `FormFiller.kt` | Read a web form from Chrome's accessibility tree, decide where each value comes from (profile, saved preference, chosen option, an answer, or ask), and fill it one checked action at a time. Never presses submit. |
| `form/ConfirmationCheck.kt` | Checks the page after submit really is a confirmation showing what was sent. SuuS only says "you're registered" when this passes. |
| `DeviceData.kt` | Reads the calendar and recent photos through Android's providers. `conflicts()` finds clashing events. |
| `DraftLedger.kt` | Records the agent's own draft edits (never the user's), so one edit can be undone while everything else stays. |

## The user

| File | What it does |
|---|---|
| `UserProfile.kt` | Name, email and phone for forms. Blank means unknown: SuuS asks instead of guessing. |
| `UserSettings.kt` | Tone, how much it explains, speech rate, robot size. Read fresh on every turn. |
| `Preferences.kt` | Explicit "remember that" instructions, scoped to a kind of task. |
| [`data/`](src/main/kotlin/com/icehacks/suus/agent/data/) | The on-device SQLite store for preferences and the run log, and the metrics export. See its README. |

Tests: `./gradlew :agent:test` (JVM, uses recorded screens from [`fixtures/screens/`](../fixtures/screens/)).
