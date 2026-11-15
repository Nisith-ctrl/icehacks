---
date: 2026-11-10
author: Advay (T06 agent)
task: T06
status: accepted
---

# Data layer: one SQLite file through the androidx.sqlite driver (no Room), plus DEBUG_RUNLOG

## Decision

- **Storage**: one SQLite file, `databases/stg_data.db`, with tables `preferences`, `run_sessions`,
  `run_events` and `meta`. Hand-written SQL on the **androidx.sqlite 2.7.1 driver API**:
  `AndroidSQLiteDriver` (framework SQLite, no native libs) on the phone, `BundledSQLiteDriver`
  (`sqlite-bundled-jvm`) in JVM unit tests. **Not Room, not DataStore.** Code:
  `agent/.../agent/data/`, how-to in its `README.md`.
- **Preferences**: `createPreferenceStore(context): ObservablePreferenceStore` (in
  `agent/.../Preferences.kt`) is the one store in the process. It extends `PreferenceStore`
  (unchanged) with `val preferences: StateFlow<List<SavedPreference>>`. Reads come from memory;
  the old `files/saved_preferences.json` is imported once (flag in `meta`) and never read again.
- **Run log**: `RunLogger` interface + `NoOpRunLogger`. The agent doesn't call it by hand:
  `createRunLog(context)` returns a `RunLog` that wraps the agent's `ScreenController`, `VoiceIO`,
  `ApprovalGate` and `AgentRunner` and logs from there. One session per spoken command.
- **Debug broadcast**: `DebugActions.RUNLOG = "com.icehacks.saythegoal.DEBUG_RUNLOG"`, extra
  `--es op export|clear|summary` (default `export`), handled by `handleRunLogDebug(context, op)`.
  Exports go to `/sdcard/Download/stg/runs/` through MediaStore (no permission).
- **Logcat tag**: `STG-Data`.
- Typed field values are never written to the run log (only the field label and length).

## Why

- Room needs KSP. [2026-11-09-build-stack-and-json](2026-11-09-build-stack-and-json.md) warns that AGP 9 compiles Kotlin itself, so
  KSP + AGP 9.5 + Kotlin 2.4 is an untested combination, and Room can't run in plain JVM unit tests
  (`./gradlew :agent:test`) without Robolectric. The driver API needs no annotation processor,
  and the same SQL runs on the JVM with the bundled driver, so the tests check the real queries.
- DataStore rewrites one file on every change. That suits preferences but not an append-only run
  log, and the task asks for tables.
- Checked: builds with AGP 9.5.1 / Kotlin 2.4.10 / compileSdk 35; 25 JVM tests; the on-device test
  and a full app run pass on an API 36 emulator.

## Alternatives considered

- Room + KSP: the "normal" choice, but it's a build risk on this stack and needs device tests.
- Jetpack DataStore for preferences + a JSONL file for runs: two storage systems, no tables.
- Framework `SQLiteOpenHelper`: no extra dependency, but can't be unit-tested on the JVM.

## Affects

- [T03](../README.md#task-board) (Susmith): `createAgent` wraps with `createRunLog` and uses
  `createPreferenceStore`; exact code in [T06](../README.md#task-board) → Requests.
- [T01](../README.md#task-board) (Susmith): add `DebugActions.RUNLOG` in `core`, its case in `DebugReceiver`,
  and `runLog.onUiEvent(...)` in the service (exact code in [T06](../README.md#task-board)).
- [T05](../README.md#task-board) (guru): the memory screen uses `createPreferenceStore(context)` (or
  `sharedPreferenceStore`) and collects `preferences`.
- [T04](../README.md#task-board) (Nisith): metrics come from the export; see `agent/.../data/README.md`.
