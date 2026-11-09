# IceHacks

Code lives in this repo. Shared team memory lives in a separate Obsidian vault repo
(`icehacks-vault`), cloned into `vault/` and gitignored here, so Obsidian's auto-sync never
touches code and agents' code commits never touch the vault.

## Teammate setup (about 10 minutes)

**Easiest:** paste [SETUP.md](SETUP.md) into Claude Code and let it do the steps below for you.

You need: Git, the GitHub CLI (`gh auth login`), Obsidian, and your AI coding tool
(Claude Code, Cursor, Codex…). Accept the GitHub invites for **both** repos first.

1. Clone both repos:

   ```bash
   gh repo clone Nisith-ctrl/icehacks
   cd icehacks
   gh repo clone Nisith-ctrl/icehacks-vault vault
   ```

2. Open Obsidian → **Open folder as vault** → pick the `icehacks/vault` folder.
   Click **Trust author and enable plugins** if asked.
3. If the Obsidian Git plugin isn't active yet: **Settings → Community plugins → Turn on
   community plugins → Browse → "Git" (by Vinzent) → Install → Enable**. The vault already
   has its settings (commit, pull and push every 2 minutes, pull on startup).
4. Settings → Git → **Commit author**: make sure your name is set (or set it globally with
   `git config --global user.name "Your Name"`).
5. Make your own session folder: `vault/team-memory/sessions/<your-name>/`.

## Running your agent

Start your agent from the repo root (not inside `vault/`) and give it your task ID:

> Read AGENTS.md, then do task T03.

`AGENTS.md` (and `CLAUDE.md`, which imports it) tells the agent to pull the vault, read the
project brief, its task, the decisions and dead ends, and to write back what it learns.

## Day-to-day

- Keep Obsidian open while you work. It syncs the vault on its own.
- Write new notes instead of editing other people's. Only edit your own task file.
- Code goes on a branch per task (`t03-short-name`) and into `main` through a PR.
