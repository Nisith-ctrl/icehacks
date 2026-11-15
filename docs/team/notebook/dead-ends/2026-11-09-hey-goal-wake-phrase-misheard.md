---
date: 2026-11-09
author: guru (T05 agent)
task: T02 (found while testing T05)
---

# "Hey Goal" is misheard by the speech recogniser, so real speech never wakes the agent

> **For whoever does T02 M1 (activation): the wake phrase must accept near misses.** Matching the
> exact words in `CommandParser.WAKE_PHRASES` is not enough with real speech.

## What we tried

Real speech on the emulator (`stg_pixel_35`, API 35 Google Play image, standard online recogniser,
host mic working). guru said "Hey Goal, what do you remember?" three times with the accessibility
service on.

## What happened

Logcat, tag `STG-Voice`:

```
heard 'Google what do you remember' -> IGNORED
heard 'hey girl what do you remember' -> IGNORED
heard 'hey girl what do you remember' -> IGNORED
```

The rest of each sentence was transcribed correctly. Only the wake words were wrong, and
`VoiceRouter` dropped all three because none starts with a phrase in `WAKE_PHRASES`. The same
sentence sent through `DEBUG_UTTERANCE` works, which is why this didn't show up before.

## Why it doesn't work (best guess)

"Hey Goal" isn't a phrase the general recogniser expects, so it picks common words that sound
alike ("hey girl", "Google"). `CommandParser.afterWake` needs an exact match on the normalised
text, so one wrong word loses the whole command.

## Do this instead

In `voice/` (T02, Susmith), when doing M1:

1. **Accept near misses of the wake phrase.** At least: "hey girl", "hey gold", "hey gol",
   "hey go", "a goal", "hey goal's", and a leading "Google" / "hey Google" when the rest of the
   sentence classifies as a command. A small edit-distance or sounds-like check on the first two
   words is safer than a growing list.
2. **Bias the recogniser** toward the wake phrases with `RecognizerIntent.EXTRA_BIASING_STRINGS`
   (API 33+), so it is more likely to return "hey goal" in the first place.
3. **Test the other phrases with real speech** ("Say the Goal", "OK Goal") and lead with whichever
   the recogniser gets right most often. If the best phrase changes, tell guru: the setup screen's
   wake-check text (`step_wake_detail`, `wake_listening` in `app/src/main/res/values/strings.xml`)
   says "Hey Goal" today.
4. Keep false wakes in mind: a looser match must still need a command after it, and Submit / Send
   still go through the approval gate, so a false wake can't commit anything.

Getting real speech into the emulator at all (needed to reproduce this): close the emulator that
Android Studio's Run button started, then run
`emulator -avd stg_pixel_35 -allow-host-audio -no-snapshot-load`. Started from the Run button it has
no host audio, and the mic is silence whatever the Extended Controls toggle says.

Related: [T02](../README.md#task-board), [T05](../README.md#task-board).
