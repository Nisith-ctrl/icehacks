---
date: 2026-11-10
author: guru (T05)
task: T05
status: accepted
---

# The app is called SuuS

> **Spelling (2026-11-10, guru):** shown as **SuuS** to users. Code identifiers use `Suus`
> (`com.icehacks.suus`, `SuusAccessibilityService`). The spoken wake word is the same word, matched
> case-insensitively.

## Decision

- The app's **display name is "Suus"**: the launcher label, the name in Settings → Accessibility,
  and the setup screen text (`app/src/main/res/values/strings.xml`, PR
  https://github.com/Nisith-ctrl/icehacks/pull/11).
- **Unchanged**: the package / application id `com.icehacks.saythegoal`, Kotlin package names,
  `DebugActions` action names, Logcat tags (`STG-*`), and the wake phrases ("Hey Goal", "Say the
  Goal", "OK Goal"). Renaming those touches `core/` and every module for no user-visible gain.
- Use "Suus" in anything users or judges see from now on: Devpost, the video, captions, docs.

## Planned next: rename everything to Suus (after Susmith's T02 work)

guru wants **everything** renamed to Suus, code included, but **after Susmith finishes his current
voice work**, so nobody's in-progress branch breaks. Until then nobody starts this rename.

- **Wake phrase becomes "Suus"** ("Hey Suus", "Suus, …") and replaces "Hey Goal" / "Say the Goal" /
  "OK Goal". **Susmith: when fixing [2026-11-09-hey-goal-wake-phrase-misheard](../dead-ends/2026-11-09-hey-goal-wake-phrase-misheard.md) today, build the
  near-miss matching so the phrase list is easy to swap**, and test "Hey Suus" with real speech
  too. Expect near misses such as "Seuss", "Zeus", "Sous", "Sus", "Suits", "Hey Sue".
- Then, as one PR when Susmith says go: the agent prompt and `ScreenText` label, package /
  applicationId `com.icehacks.suus` (moves every source folder), `DebugActions` names, Logcat tags,
  the setup screen's wake-check text, docs, fixtures and the project brief.
- Whoever does it: announce it in the team chat first, merge everything else before it, and have
  everyone pull right after. It touches about 46 files.

### How the rename starts (so nothing overlaps)

guru's agent does the rename, and it **only starts on Susmith's go**:

1. **Susmith**, when your voice work is merged and you have **nothing unpushed** in `core/`,
   `voice/`, `agent/`, `device/`, `app/` or `overlay/`, create the file
   `team-memory/sessions/susmith/<date>-suus-rename-go.md` with one line, e.g.
   `Go for the Suus rename. Nothing unpushed.` That's the signal. (Marking T01, T02 and T03 all
   `done` also counts.)
2. guru's agent checks the vault about every 20 minutes. On the signal it writes
   `team-memory/sessions/guru/<date>-suus-rename-LOCK.md` ("rename in progress, don't push code").
   **While that file exists, nobody pushes code to `main` or opens code PRs.**
3. It branches from the latest `main` (after PR 11 is merged, or with PR 11's commits included),
   renames everything, builds and tests, opens one PR, and posts the link in the lock file.
4. After the PR is merged, the lock file is replaced by a `...-suus-rename-DONE.md` note:
   everyone runs `git pull` and rebases their branches. Then normal work resumes.

In the vault, guru's agent only edits guru's own files (T05 task file, this note, guru's
sessions). **Nisith**: the project brief, assignments and build plan are yours to update to "Suus"
and "Hey Suus" after the DONE note appears.

## Why

guru chose a short, sayable product name for the frontend.

## Alternatives considered

- Keep "Say the Goal" as the app name: longer, and it doubles as a wake phrase, which is confusing
  in the setup screen.
- Rename the package too: risky this close to feature freeze, and invisible to users.

## Affects

- [T05](../README.md#task-board) (done in PR 11).
- **Susmith (T03)**: the agent's system prompt still says "You are Say the Goal"
  (`agent/.../AgentPrompt.kt`), and `ScreenText.kt` labels our own package "Say the Goal". Change
  both to "Suus" if the agent ever says its name aloud.
- **Susmith (T02)**: the wake phrases stay as they are unless you decide otherwise. "Hey Suus" is
  an option to try with real speech when fixing [2026-11-09-hey-goal-wake-phrase-misheard](../dead-ends/2026-11-09-hey-goal-wake-phrase-misheard.md), but
  it isn't decided.
- **Nisith (T04 M3/M4)**: docs, Devpost and the video use "Suus". The project brief still says
  "Say the Goal".
