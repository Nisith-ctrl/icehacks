---
date: 2026-11-09
author: Susmith (T01 agent)
task: T01
status: accepted
---

# Build stack (AGP 9, built-in Kotlin) and snapshot/action JSON

## Decision

- **Versions** (in `gradle/libs.versions.toml`): AGP 9.5.1, Gradle 9.9.0 (wrapper), Kotlin 2.4.10,
  coroutines 1.10.2, kotlinx.serialization 1.9.0. `compileSdk 35`, `minSdk 30`, `targetSdk 35`,
  Java 17 bytecode. Gradle runs on Android Studio's bundled JDK (25).
- **AGP 9 compiles Kotlin itself.** Android modules apply only `com.android.library` /
  `com.android.application`. Do **not** add `org.jetbrains.kotlin.android`, and there is no
  `kotlinOptions {}` block. Sources live in `src/main/kotlin`. `core` is a plain Kotlin/JVM module
  (`org.jetbrains.kotlin.jvm` + serialization plugin), so its code can't use Android APIs.
- **JSON**: use `StgJson` from `core` (`com.icehacks.saythegoal.core.StgJson`) for snapshots,
  actions and results everywhere (fixtures, debug broadcasts, tests). Sealed types use a `"type"`
  discriminator with short names: `{"type":"Tap","nodeId":"n5","isCommit":false}`,
  `{"type":"Back"}`, `{"type":"Done","after":{...},"screenChanged":true}`.
  `UserCommand`, `ApprovalResult` and the enums used by voice/overlay are not serializable (not needed).
- Module factories exist with these exact signatures (fakes for now; owners replace the body):
  `voice`: `createVoice(context, scope)`, `createApprovalGate(voice, fingerprinter)`;
  `overlay`: `createOverlay(service)`; `agent`: `createAgent(screen, voice, approval, overlay, context)`.

## Why

Android Studio 2026 bundles JDK 25, and Gradle 8.x / AGP 8.x can't run on it. Using the current
stack means every teammate's Android Studio opens and builds the project without a JDK change.
A short discriminator keeps adb broadcasts and fixture files readable.

## Alternatives considered

- AGP 8.7 + Gradle 8.10 + JDK 17: works, but every teammate would need to install JDK 17 and set
  Gradle JDK by hand.

## Affects

All tasks. [T02](../README.md#task-board), [T03](../README.md#task-board), [T04](../README.md#task-board): build
files in your module follow the AGP 9 rules above.
