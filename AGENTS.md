# Agent instructions (read this first)

You are one of several AI agents working on the same hackathon project. Each agent is run by a
different teammate on their own machine. You share context through the Obsidian vault in `vault/`,
which is its own git repo and syncs to GitHub every couple of minutes.

## Before you start any work

1. Sync the vault: `git -C vault pull --rebase --autostash`
2. Read, in this order:
   - `vault/team-memory/project-brief.md`: what we're building, stack, conventions
   - `vault/tasks/` — find **your** task file (your teammate will tell you its ID, e.g. `T03`).
     Read its scope, the files you own, acceptance criteria and dependencies.
   - Every file in `vault/team-memory/decisions/`: these are binding. Don't contradict them
     without asking your human.
   - Every file in `vault/team-memory/dead-ends/` so you don't repeat failed approaches.
   - `vault/team-memory/api-notes/` for any API or service your task touches.
   - Task files of the tasks yours depends on, especially their **Handoff** sections.
3. If something in your task conflicts with a decision or another task's scope, stop and tell your
   human instead of guessing.

## While you work

- Stay inside the files/folders your task file lists under **Owns**. If you need to change something
  another task owns, write it in your task file under **Requests to other tasks** and tell your human.
- Work on a branch named after your task: `t03-short-name`. Never commit straight to `main`.
- Keep the shared contracts (API shapes, DB schema, env var names) exactly as written in the
  project brief and decisions. If a contract has to change, record a new decision first.

## Writing to team memory

Other agents only know what you write down. Write a note when you:

| Situation | Where | Template |
|---|---|---|
| Make a choice others must follow (library, schema, endpoint shape, naming) | `vault/team-memory/decisions/YYYY-MM-DD-short-title.md` | `vault/templates/decision.md` |
| Try something that doesn't work | `vault/team-memory/dead-ends/YYYY-MM-DD-short-title.md` | `vault/templates/dead-end.md` |
| Learn how an external API/service actually behaves (auth, quirks, rate limits, example calls) | `vault/team-memory/api-notes/<service>.md` | `vault/templates/api-note.md` |
| Finish a work session | `vault/team-memory/sessions/<your-human>/YYYY-MM-DD-<task-id>.md` | `vault/templates/session-log.md` |

Rules that keep the vault free of merge conflicts:

- **Create new files; don't edit other people's files.** The only shared file you may edit is your
  own task file in `vault/tasks/`.
- Never put secrets (API keys, tokens, passwords) in the vault. Write the env var name instead.
- Keep notes short and factual. Link related notes with `[[note-name]]`.

## When you finish (or stop for the day)

1. Update your task file: set `status:` in the frontmatter, fill in **Handoff** (what's done, what's
   not, how to run/test it, anything the next agent must know).
2. Write your session log.
3. Push your code branch and open a PR (`gh pr create`) if the task is done.
4. Sync the vault:
   ```bash
   git -C vault add -A
   git -C vault commit -m "memory: <task-id> <short summary>"
   git -C vault pull --rebase --autostash
   git -C vault push
   ```
   If git reports `index.lock` exists, Obsidian Git is syncing at the same moment. Wait a few
   seconds and retry.
