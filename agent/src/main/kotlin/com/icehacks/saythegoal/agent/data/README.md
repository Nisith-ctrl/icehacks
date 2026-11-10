# Data layer (T06): saved preferences, run log, metrics export

One SQLite file on the phone, `databases/stg_data.db`, with three tables: `preferences`,
`run_sessions` and `run_events` (plus `meta`). Hand-written SQL on the androidx.sqlite driver API
(framework SQLite on the phone, bundled SQLite in JVM tests), no Room. Why: vault decision
`2026-11-10-data-store-sqlite-driver`.

| File | What |
|---|---|
| `StgDatabase.kt` | Schema and every query. One connection, one lock. |
| `DbPreferenceStore.kt` | `PreferenceStore` on the `preferences` table, with a live `StateFlow`, plus the one-time import from the old JSON file. `MemoryPreferenceStore` is the fallback if the database won't open. |
| `RunLogger.kt` | The `RunLogger` interface, `NoOpRunLogger`, stored rows, `RunSummaries` (the metric counts). |
| `DbRunLogger.kt` | `RunLogger` on the `run_*` tables. |
| `RunLog.kt` | Wrappers that log for the agent: `screen()`, `voice()`, `approval()`, `agent()`, plus `onUiEvent()` for user touches. |
| `RunExport.kt` | JSON + CSV export and the one-line summary. |
| `StgData.kt` | Process-wide singletons, `createRunLog(context)`, `handleRunLogDebug(context, op)`. |

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
line the agent says, "stop" and user touch is an **event** in the running session (or the last one,
if none is running). Typed values are never logged, only the field label and the length.

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

**Wiring** (Susmith, `AgentFactory.kt` + the service; the exact code is in the T06 task file):
wrap the agent's `screen`, `voice` and `approval` with `createRunLog(context)` and wrap the
returned `AgentRunner` with `runLog.agent(...)`. Nothing in `AgentLoop` has to change.

### Summary numbers (`RunSummaries`)

| Field | Counts |
|---|---|
| `actions` / `failedActions` | device actions the agent performed / that didn't succeed |
| `spokenCommands` | the command that started each session (activation is part of that utterance) + answered questions + approvals answered with the commit word or Cancel + "stop" |
| `questions`, `approvals`, `approvalsGranted`, `undos` | as named |
| `userTouches` | clicks the accessibility service saw in other apps while the agent was not acting (and not in the 1.5 s after an action). **A lower bound**: it can miss touches that don't produce a click event (e.g. some keyboard taps, swipes). Check it against the recording. |
| `durationMs` / `spanMs` | time inside sessions / first start to last end |

## Export for metrics (Nisith)

Needs the `DEBUG_RUNLOG` broadcast wired in `DebugReceiver` (debug builds; see the T06 task file).

```bash
# before each recorded run: start from an empty log
adb shell am broadcast -a com.icehacks.saythegoal.DEBUG_RUNLOG --es op clear
# ... do the run ...
adb shell am broadcast -a com.icehacks.saythegoal.DEBUG_RUNLOG --es op summary   # totals in Logcat
adb shell am broadcast -a com.icehacks.saythegoal.DEBUG_RUNLOG                    # op defaults to export
adb logcat -d -s STG-Data
adb pull /sdcard/Download/stg/runs/ .
```

Each export writes three files to `/sdcard/Download/stg/runs/` (via MediaStore, no permission):

- `stg-runs-<yyyyMMdd-HHmmss>.json`: totals, then every session with its summary and events.
- `stg-sessions-<stamp>.csv`: one row per session with the summary counts, and a `TOTAL` row.
- `stg-events-<stamp>.csv`: every event.

If shared Downloads refuses the write, the files go to
`/sdcard/Android/data/com.icehacks.saythegoal/files/runs/` instead (Logcat says which).

Raw database, debug builds, no broadcast needed:
`adb exec-out run-as com.icehacks.saythegoal cat databases/stg_data.db > stg_data.db`.

## Tests

- `./gradlew :agent:test`: `agent/src/test/.../data/` runs the real SQL on the JVM (save,
  overwrite, remove, clear, restart, JSON import, live flow, run log through the real agent loop,
  touches, export).
- `./gradlew :agent:connectedDebugAndroidTest` with an emulator running: the framework driver,
  the shared instance, the import, and the export into Downloads.
