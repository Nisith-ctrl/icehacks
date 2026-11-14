# Data layer (T06): saved preferences, run log, metrics export

One SQLite file on the phone, `databases/suus_data.db`, with three tables: `preferences`,
`run_sessions` and `run_events` (plus `meta`). Hand-written SQL on the androidx.sqlite driver API
(framework SQLite on the phone, bundled SQLite in JVM tests), no Room. Why: vault decision
`2026-11-10-data-store-sqlite-driver`.

| File | What |
|---|---|
| `SuusDatabase.kt` | Schema and every query. One connection, one lock. |
| `DbPreferenceStore.kt` | `PreferenceStore` on the `preferences` table, with a live `StateFlow`, plus the one-time import from the old JSON file. `MemoryPreferenceStore` is the fallback if the database won't open. |
| `RunLogger.kt` | The `RunLogger` interface, `NoOpRunLogger`, stored rows, `RunSummaries` (the metric counts). |
| `DbRunLogger.kt` | `RunLogger` on the `run_*` tables. |
| `RunLog.kt` | Wrappers that log for the agent: `screen()`, `voice()`, `approval()`, `agent()`, plus `onUiEvent()` for user touches. |
| `RunExport.kt` | JSON + CSV export and the one-line summary. |
| `SuusData.kt` | Process-wide singletons, `createRunLog(context)`, `handleRunLogDebug(context, op)`. |

`createPreferenceStore(context)` is in `../Preferences.kt`, next to `PreferenceStore`.

## Saved preferences

```kotlin
val store = createPreferenceStore(context)   // the same instance everywhere in the app
store.all()                                  // List<SavedPreference>, in save order, from memory (no disk read)
store.preferences                            // StateFlow<List<SavedPreference>>: emits after every change
store.save(p); store.remove(scope, key); store.clear()
```

- One value per `scope` + `key`; saving again replaces it and moves it to the end.
- The first time the database opens, anything in the old `files/saved_preferences.json` is
  copied in (never over a value already in the database). It is never read again after that, even
  if the file is still there, so a forgotten preference can't come back.
- Safe to call from the UI thread: reads come from memory, and a write is one small SQLite statement.

"What I remember" screen (T05): `lifecycleScope.launch { repeatOnLifecycle(STARTED) { store.preferences.collect(::render) } }`.

## Run log

One **session** per spoken command (`goal`, `undo`, `memory`). Every action, question, approval,
line the agent says, "stop" and user touch is an **event** in the running session. Typed values are
never logged, only the field label and the length.

Between commands, an event goes on the session that just ended, but only for 60 s after its end
(`DbRunLogger.AFTER_RUN_GRACE_MS`): a "stop" right after it cancelled the run, or the user fixing
something by hand, belongs to that run; setting up the phone or saying "stop" an hour later
doesn't, and is dropped. Nothing is logged before the first command or after an app restart until
the next command.

| Event `type` | `name` | `label` | `detail` | `ok` |
|---|---|---|---|---|
| `action` | `tap` / `submit` / `type` / `scroll` / `back` / `launch` | target label or package | e.g. `11 chars`, `down`, failure reason | done? |
| `question` | | question | answer (empty = no answer) | answered? |
| `approval` | commit word | read-back | `APPROVED` / `CANCELLED` / `NO_ANSWER` | approved? |
| `said` | | what the agent said | | |
| `stop` | | `stop` | | |
| `touch` | | e.g. `tap in com.android.chrome` | | |

Session `outcome`: `done` / `stopped` / `cancelled` (from the agent loop, if it reports it),
otherwise `finished` with the agent's last spoken line as the detail, `stopped_by_user` (Stop
cancelled it), `error`, or `unknown` (a new command started first).

**Wiring** (done in PR 21): `createAgent` in `AgentFactory.kt` wraps the agent's `screen`, `voice`
and `approval` with `createRunLog(context)` and the returned `AgentRunner` with `runLog.agent(...)`;
`SuusAccessibilityService` passes its events to `runLog.onUiEvent(...)` for the touch count.
Nothing in `AgentLoop` calls the log.

### Summary numbers (`RunSummaries`)

| Field | Counts |
|---|---|
| `actions` / `failedActions` | device actions the agent performed / that didn't succeed |
| `spokenCommands` | the command that started each session (activation is part of that utterance) + answered questions + approvals answered with the commit word or Cancel + "stop" |
| `questions`, `approvals`, `approvalsGranted`, `undos` | as named |
| `userTouches` | clicks the accessibility service saw in other apps while the agent was not acting (and not in the 1.5 s after an action), during the session or in the 60 s after it. **A lower bound**: it can miss touches that don't produce a click event (e.g. some keyboard taps, swipes). Check it against the recording. |
| `durationMs` / `spanMs` | time inside sessions / first start to last end |

## Export for metrics (Nisith)

Uses the `DEBUG_RUNLOG` broadcast (`DebugReceiver`, debug builds only).

```bash
# before each recorded run: start from an empty log
adb shell am broadcast -a com.icehacks.suus.DEBUG_RUNLOG --es op clear
# ... do the run, then export within a minute or two (touches up to 60 s after it still count) ...
adb shell am broadcast -a com.icehacks.suus.DEBUG_RUNLOG --es op summary   # totals in Logcat
adb shell am broadcast -a com.icehacks.suus.DEBUG_RUNLOG                    # op defaults to export
adb logcat -d -s SUUS-Data
adb pull /sdcard/Download/suus/runs/ .
adb shell rm -r /sdcard/Download/suus/runs                                  # always, see below
```

(Git Bash on Windows: put `MSYS_NO_PATHCONV=1` in front of adb commands that take a `/sdcard/...`
path, or the path is rewritten and the command fails.)

**Delete the exports from the phone as soon as you have pulled them.** They are new files in
Downloads, so the phone's file picker lists them at the top of **Recent files**, above the photos.
Demo 2 attaches its photo through that picker: on Nov 13 a Demo 2 run failed with Recent full of
run exports and debug snapshots (PR 38). Checked on an API 36 emulator: with exports on the phone
the first screen of Recent was all `suus-*.csv`; after the `rm` it was empty.

- `summary` only writes to Logcat, so it never adds a file. Use it when the counts are all you need.
- Before recording, start clean: `adb shell rm -r /sdcard/Download/suus` (this also removes
  `DEBUG_SNAPSHOT` files).

Each export writes three files to `/sdcard/Download/suus/runs/` (via MediaStore, no permission):

- `suus-runs-<yyyyMMdd-HHmmss>.json`: totals, then every session with its summary and events.
- `suus-sessions-<stamp>.csv`: one row per session with the summary counts, and a `TOTAL` row.
- `suus-events-<stamp>.csv`: every event.

If shared Downloads refuses the write, the files go to
`/sdcard/Android/data/com.icehacks.suus/files/runs/` instead (Logcat says which).

Raw database, debug builds, no broadcast needed:
`adb exec-out run-as com.icehacks.suus cat databases/suus_data.db > suus_data.db`.

## Tests

- `./gradlew :agent:test`: `agent/src/test/.../data/` runs the real SQL on the JVM (save,
  overwrite, remove, clear, restart, JSON import, live flow, run log through the real agent loop,
  touches, export).
- `./gradlew :agent:connectedDebugAndroidTest` with an emulator running: the framework driver,
  the shared instance, the import, and the export into Downloads.
