# core: shared contracts

The interfaces and data types every other module builds against. It has no Android code, so the
modules could be written in parallel and tested on the JVM, and they fit together because none of
them can change these shapes on its own.

| File | What's in it |
|---|---|
| `Contracts.kt` | The screen model (`ScreenSnapshot`, `UiNode`, `Bounds`, `NodeSignature`), `DeviceAction` and `ActionResult`, the module interfaces (`ScreenController`, `VoiceIO`, `ApprovalGate`, `Overlay`, `AgentRunner`), `Approval` and `Fingerprinter`, `UserCommand`, and the debug broadcast names. |
| `SuusJson.kt` | The one JSON configuration for snapshots, actions and results, used by fixtures, debug broadcasts and tests. Sealed types use a short `type` field, e.g. `{"type":"Tap","nodeId":"n12"}`. |

Who implements what:

| Interface | Module |
|---|---|
| `ScreenController`, `Fingerprinter` | [`device`](../device/) |
| `VoiceIO`, `ApprovalGate` | [`voice`](../voice/) |
| `AgentRunner` | [`agent`](../agent/) |
| `Overlay` | [`overlay`](../overlay/) |

`app` wires them together in `SuusAccessibilityService`.
