# Teammate setup: instructions for your AI agent

**Teammate:** paste this whole file into Claude Code (or a similar agent), started in the folder
where you want the project to live (e.g. `Documents`). It sets everything up and tells you the
few steps only you can do.

---

You are setting up this machine for a hackathon team. The team shares two private GitHub repos
owned by `Nisith-ctrl`:

- `icehacks`: the code. Contains `AGENTS.md`/`CLAUDE.md` with the agent working rules.
- `icehacks-vault`: a shared Obsidian vault (team memory and tasks). Obsidian's Git plugin
  syncs it every 2 minutes. It must be cloned **inside** the code repo as `vault/` (the code repo
  gitignores that folder).

Work through the steps below in order. Detect the OS first and use commands that fit it (PowerShell
or Git Bash on Windows, a normal shell on macOS/Linux). Ask the human before installing any
software. Never ask for, type or store passwords or tokens; if something needs a login, have
the human do it themselves.

## 0. Clean up the old setup (if there is one)

The team previously used repos named `hackathon` and `hackathon-vault`. They are now archived
(read-only) and must not be used. Look for a `hackathon` folder from that setup (check the current
folder and the human's Documents folder). If you find one:

- Tell the human it's from the old, archived setup. Don't clone or work inside it.
- Tell them to switch Obsidian away from the old vault: click the vault name at the bottom left
  → **Manage vaults** → remove `hackathon/vault` from the list (this only forgets it, it doesn't
  delete files).
- Offer to delete the old `hackathon` folder. Only delete it if the human says yes.

## 1. Check the tools

Check for each and report versions:

- `git --version`
- `gh --version` (GitHub CLI)
- Obsidian. On Windows look for `%LOCALAPPDATA%\Programs\Obsidian\Obsidian.exe` or
  `%APPDATA%\obsidian`. On macOS look for `/Applications/Obsidian.app`.

If something is missing, say what it is and how to install it (git-scm.com, cli.github.com,
obsidian.md, or `winget install Git.Git GitHub.cli Obsidian.Obsidian` /
`brew install git gh` + `brew install --cask obsidian`). Install only if the human says yes.

## 2. GitHub login and git identity

- Run `gh auth status`. If it isn't logged in, tell the human to run `gh auth login`
  themselves (in Claude Code they can type `! gh auth login`), choosing GitHub.com, HTTPS,
  and browser login. Wait until they say it's done, then check again.
- Run `gh auth setup-git` so git uses the gh login for HTTPS.
- Check `git config --global user.name` and `user.email`. If either is empty, ask the human
  what to use and set them.

## 3. Accept the repo invitations

List pending invitations:

```bash
gh api user/repository_invitations --jq '.[] | "\(.id) \(.repository.full_name)"'
```

If invitations for `Nisith-ctrl/icehacks` or `Nisith-ctrl/icehacks-vault` are listed, show
them to the human and, once they confirm, accept each (ignore any for the old `hackathon` repos):

```bash
gh api -X PATCH user/repository_invitations/<id>
```

Then confirm access: `gh repo view Nisith-ctrl/icehacks` and
`gh repo view Nisith-ctrl/icehacks-vault`. If either fails and no invitation is listed,
stop and tell the human to ask Nisith to invite their GitHub username.

## 4. Clone both repos

From the current folder (confirm it with the human first):

```bash
gh repo clone Nisith-ctrl/icehacks
gh repo clone Nisith-ctrl/icehacks-vault icehacks/vault
```

Check that `icehacks/vault/.obsidian/plugins/obsidian-git/main.js` exists and that
`git -C icehacks status` doesn't list `vault/`.

## 5. Create the teammate's session folder

Ask the human for the short name they want to go by (e.g. their first name, lowercase). Then:

- Create `icehacks/vault/team-memory/sessions/<name>/README.md` containing one line:
  `Session logs for <name>.`
- Add them to the Team table in `icehacks/vault/team-memory/project-brief.md` **only if** the
  human asks you to. That file is owned by the team lead.
- Commit and push the vault:

  ```bash
  git -C icehacks/vault add -A
  git -C icehacks/vault commit -m "Add session folder for <name>"
  git -C icehacks/vault pull --rebase --autostash
  git -C icehacks/vault push
  ```

## 6. Steps only the human can do in Obsidian

Print these for the human, with the full absolute path of `icehacks/vault` filled in:

1. Open Obsidian → **Open folder as vault** → pick `<absolute path>/icehacks/vault`.
2. When asked, click **Trust author and enable plugins**. If not asked: **Settings →
   Community plugins → Turn on community plugins**, then make sure **Git** is switched on.
3. A Git status should appear at the bottom right of Obsidian. Keep Obsidian open while working
   so the vault keeps syncing.

## 7. Check it works

- Read `icehacks/AGENTS.md` and `icehacks/vault/team-memory/project-brief.md`, and list the
  task files in `icehacks/vault/tasks/`.
- Give the human a short summary: where everything is, which tasks exist and who owns them, and
  this reminder on how to start work:

  > Open a new agent session in the `icehacks` folder (not `vault/`) and say:
  > "Read AGENTS.md and tell me what I'm assigned."
  > (The agent finds your section in `vault/team-memory/assignments.md` and walks you through it.)
