# The team notebook (public snapshot)

SuuS was built in one week by four people, each working with their own AI coding agent (Claude
Code) on their own machine. The agents couldn't talk to each other, so we gave them a shared memory:
a private Obsidian vault, synced through GitHub every two minutes. Every agent read the plan, its
task, and all of the team's decisions and dead ends before it started, and wrote back what it learned.

This folder is a snapshot of that vault's **decisions** and **dead ends**, copied on Nov 15, 2026.
They're unedited, so some details are historical. The app was first called "Say the Goal", so you'll
see `saythegoal`, `stg` and "Hey Goal", and a few notes mention the Galaxy S21 we later dropped.
Links to notes that stayed private are marked.

## How it worked

```
 lead: project brief + fixed contracts (core interfaces, JSON shapes, env var names)
   │
   ├─▶ 6 task files, cut along folder boundaries, each owned by one person and their agent
   │
   ▼
 each agent, before starting:  read the brief → its task → every decision → every dead end
   │
   ▼
 work on one branch per task ──▶ pull request ──▶ teammate review ──▶ main
   │
   ▼
 write back as NEW files (never edit someone else's):
   decisions/   binding choices        dead-ends/   what failed and why
   api-notes/   how services behave    sessions/    a log of every agent session
   │
   ▼
 Obsidian Git syncs the vault every 2 minutes, so the next agent starts with what this one learned
```

The rules every agent followed are in the code repo's [`AGENTS.md`](../../../AGENTS.md), and the
human side is in [workflow.md](../workflow.md).

## By the numbers

| | |
|---|---|
| Teammates, each with their own agent | 4 |
| Task files | 6 |
| Pull requests merged | 57 |
| Commits to the shared vault | 118 |
| Agent session logs | 23 |
| Decisions | 9 |
| Dead ends | 5 |
| API notes | 4 |

## Two stories from the notebook

- **The wake word.** guru's agent found that real speech turned "Hey Goal" into "hey girl" and
  "Google", so the agent never woke ([dead end](dead-ends/2026-11-09-hey-goal-wake-phrase-misheard.md)).
  The team renamed the app SuuS ([decision](decisions/2026-11-10-app-name-suus.md)), and Susmith's
  agent built sound-alike matching for "Suus" ([decision](decisions/2026-11-10-wake-word-suus.md)).
  The rename touched about 46 files, so it ran on a written go/lock/done signal in the vault so no
  one's branch broke mid-rename.
- **Demo 2 moved apps.** The emulator can't send photo texts and hides the Messages text box from
  accessibility ([dead end](dead-ends/2026-11-11-emulator-no-mms.md),
  [dead end](dead-ends/2026-11-09-messages-compose-a11y-gaps.md)), so the photo hand-off moved to a
  Gmail draft ([decision](decisions/2026-11-13-demo2-gmail-draft.md)). The next agent to touch
  Gmail then logged why "Insert photo" breaks undo
  ([dead end](dead-ends/2026-11-13-gmail-insert-photo-and-stale-nodes.md)).

## Task board

| Task | Owner | Scope |
|---|---|---|
| T01 | Susmith | Android app shell, reading the screen, validated taps and typing |
| T02 | Susmith | Wake word, spoken questions, spoken approval, Stop |
| T03 | Susmith | The agent loop, form filling, saved preferences and undo |
| T04 | Nisith | The robot overlay, test forms and data, metrics, docs, the demo and Devpost |
| T05 | Guru | Setup and memory screens, the robot's final look, the APK and relay |
| T06 | Advay | The on-device database: saved preferences and the run log |

## Decisions

| Date | Decision |
|---|---|
| Nov 9 | [Architecture and module split](decisions/2026-11-09-architecture-and-module-split.md) |
| Nov 9 | [Build stack (AGP 9, built-in Kotlin) and snapshot/action JSON](decisions/2026-11-09-build-stack-and-json.md) |
| Nov 9 | [The agent uses Groq (free tier), not the Anthropic API](decisions/2026-11-09-llm-provider-groq.md) |
| Nov 9 | [Test form URLs](decisions/2026-11-09-test-form-urls.md) |
| Nov 10 | [The app is called SuuS](decisions/2026-11-10-app-name-suus.md) |
| Nov 10 | [Data layer: one SQLite file through the androidx.sqlite driver](decisions/2026-11-10-data-store-sqlite-driver.md) |
| Nov 10 | [The wake word is "Suus"](decisions/2026-11-10-wake-word-suus.md) |
| Nov 12 | [We test and record on the emulator only](decisions/2026-11-12-emulator-only.md) |
| Nov 13 | [Demo 2 is a Gmail draft, not Messages](decisions/2026-11-13-demo2-gmail-draft.md) |

## Dead ends

| Date | What didn't work |
|---|---|
| Nov 9 | ["Hey Goal" is misheard by the speech recogniser](dead-ends/2026-11-09-hey-goal-wake-phrase-misheard.md) |
| Nov 9 | [Google Messages hides its compose box from accessibility](dead-ends/2026-11-09-messages-compose-a11y-gaps.md) |
| Nov 9 | [Installing the Android SDK into AppData from the Claude desktop app](dead-ends/2026-11-09-sdk-in-appdata-from-claude-desktop.md) |
| Nov 11 | [The emulator can't attach photos in Messages (no MMS)](dead-ends/2026-11-11-emulator-no-mms.md) |
| Nov 13 | [Gmail's "Insert photo" isn't an attachment, and removed attachments linger](dead-ends/2026-11-13-gmail-insert-photo-and-stale-nodes.md) |
