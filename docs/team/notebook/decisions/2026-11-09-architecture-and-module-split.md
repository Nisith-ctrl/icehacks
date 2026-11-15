---
date: 2026-11-09
author: Nisith
task: all
status: accepted
---

# Architecture and module split

## Decision

- One Android app in Kotlin, split into Gradle modules `core`, `app`, `device`, `voice`, `agent`,
  `overlay`. Feature modules depend only on `core`; `app` wires them together. One task owns each
  module (see `project-brief` (private notes) → Repo layout).
- All cross-module calls go through the interfaces in `core` (copied verbatim from the brief).
  Nobody calls into another feature module directly.
- Every module ships a `Fake…` implementation so the app builds and runs with any module unfinished.
- Approval is enforced in the device layer: `ScreenController.perform` refuses a commit tap
  (Submit/Send) unless the approval's fingerprint matches the current screen. The agent cannot
  skip it by accident.
- The agent uses the Claude Messages API with tool use, model `claude-sonnet-5-5`, key in
  `local.properties` as `ANTHROPIC_API_KEY`. The model picks one next action at a time from a
  small tool set; code validates and executes it.
- Calendar conflicts are checked by reading `CalendarContract` (reliable), while the agent also
  opens Calendar on screen so the viewer sees it check. Photos are found through `MediaStore` by
  date; the visible attach happens through the Messages UI.
- Debug hooks are adb broadcasts (`DebugActions` in `core`) so each module can be tested alone,
  without speaking or a finished agent.

## Why

Four people, four agents, one APK. Fixed interfaces and fakes let everyone build and test in
parallel and integrate at the checkpoints without rewriting each other's code.

## Alternatives considered

- Separate apps talking over IPC: more moving parts, no benefit for a demo.
- A backend server for the agent: adds deployment and latency; the phone can call the API directly
  for a hackathon prototype (test key only).
- Reading calendar only through the UI: fragile and slow for conflict checking.

## Affects

All tasks: [T01](../README.md#task-board), [T02](../README.md#task-board), [T03](../README.md#task-board),
[T04](../README.md#task-board).
