# Agent instructions (read this first)

You are one of several AI agents working on the same hackathon project. Each agent is run by a
different teammate on their own machine. You share context through the Obsidian vault in `vault/`,
which is its own git repo and syncs to GitHub every couple of minutes.

## Before you start any work

1. Sync both repos: `git pull` (code, on `main` or your branch) and
   `git -C vault pull --rebase --autostash` (vault).
2. Find out who you're working for and what they're assigned:
   - Read `vault/team-memory/assignments.md`. Match your human by `gh api user --jq .login` or
     `git config user.name` against its table; if neither matches, ask them their name.
   - **Before doing any work, show them their section of that file**: their tasks, the next steps
     in order, and what "done" means. Then ask which step to start with (default: the first one
     not done yet). Do this even if they only said "start" or "what should I do".
   - If they named a task ID that isn't in their section, say so and ask before going on.
3. Read, in this order:
   - `vault/team-memory/project-brief.md`: what we're building, stack, conventions
   - `vault/tasks/`: the task file(s) from their assignment. Read its scope, the files you own,
     acceptance criteria, dependencies and the **Handoff** section (what's already done).
   - Every file in `vault/team-memory/decisions/`: these are binding. Don't contradict them
     without asking your human.
   - Every file in `vault/team-memory/dead-ends/` so you don't repeat failed approaches.
   - `vault/team-memory/api-notes/` for any API or service your task touches.
   - Task files of the tasks yours depends on, especially their **Handoff** sections.
4. If something in your task conflicts with a decision or another task's scope, stop and tell your
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
