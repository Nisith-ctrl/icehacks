---
date: 2026-11-10
author: Susmith (T02)
task: T02
status: accepted
---

# The wake word is "Suus" (replaces "Hey Goal")

## Decision

- Say **"Suus"** (like "soose") at the start, optionally after "hey" / "hi" / "ok", then the
  request: "Suus, find the library workshop invite and register me." "Suus" alone wakes the agent
  and it answers "Yes?".
- The recognizer doesn't know the word, so `CommandParser.afterWake` matches it **by sound**: an
  s/z sound, an "oo" vowel in any common spelling, then an s/z ending. So suus, sous, soos, sues,
  Sue's, Seuss, Zeus, zoos, sus, suss and suse all count, and splits like "sue us" / "su us" are
  joined back up. The recognizer is also biased toward "suus" (`EXTRA_BIASING_STRINGS`, API 33+).
- **"Hey Goal" no longer wakes the agent.**
- Code: PR https://github.com/Nisith-ctrl/icehacks/pull/13 (voice module).

## Why

Real speech turned "Hey Goal" into "hey girl" / "Google …" ([2026-11-09-hey-goal-wake-phrase-misheard](../dead-ends/2026-11-09-hey-goal-wake-phrase-misheard.md)).
The team chose a short, distinctive word, and the app is now called Suus (T05).

## Alternatives considered

- Keep "Hey Goal" with fuzzy matching: it works in tests, but "Google …" overlaps with Assistant
  and "a goal" is common speech.

## Affects

- [T05](../README.md#task-board) (guru): the setup wake-check strings (`step_wake_detail`, `wake_listening`)
  and any "Hey Goal" text should say "Suus". Use `wakeSignals(voice)` for the check.
- [T04](../README.md#task-board) (Nisith): the demo script, docs and Devpost say "Suus, …".
- Known trade-off: a sentence that starts with "Sue's", "Zeus" or "sous" will wake the agent. A
  false wake can't submit or send anything (spoken approval is still required), but it may start
  acting on the rest of the sentence. Say "Suus, stop" or "stop" to cancel.
