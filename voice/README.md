# voice: listening, speaking and spoken approval

| File | What it does |
|---|---|
| `AndroidVoice.kt` | The real `VoiceIO`: an always-on `SpeechRecognizer` loop (the ear) and `TextToSpeech` (the mouth). The ear pauses while SuuS speaks, so it never hears itself. |
| `VoiceRouter.kt` | Decides where each transcript goes. A waiting question or approval gets it first; otherwise "Suus, …" starts a command, and a bare "Suus" opens a short follow-up window. |
| `CommandParser.kt` | Fast, rule-based routing: the wake word and the sound-alikes the recognizer writes for it ("sous", "Seuss", "cease"…), Stop, Undo, memory commands. The agent does the deeper understanding. |
| `ChoiceMatcher.kt` | Maps an answer to one of the offered choices: "the second one", "email please", "use the one by the door". Returns nothing when the match isn't clear. |
| `SpokenApprovalGate.kt` | Speaks the read-back, then accepts only the commit word ("Submit" or "Send") or "Cancel". "Yes" or "ok" is not approval. The approval carries the fingerprint of the screen that was read back. |
| `VoiceStyle.kt` | How SuuS sounds, from the user's choices in the app. |
| `VoiceFactory.kt` | Builds the voice layer for the service. Debug builds also accept typed commands (below). |

## Typed commands (debug builds)

Handled exactly like recognized speech: it answers a waiting question, or starts a command.

```bash
adb shell am broadcast -a com.icehacks.suus.DEBUG_UTTERANCE --es text "'Suus, open Gmail'" -p com.icehacks.suus
```

[`scripts/demo-keys.sh`](../scripts/demo-keys.sh) sends the demo lines with one key each.

Logcat tag: `SUUS-Voice`.
