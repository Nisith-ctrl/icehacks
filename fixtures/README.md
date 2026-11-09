# Fixtures

| Folder | What | Owner |
|---|---|---|
| `forms/` | The two controlled test forms + confirmation pages, and the team-only seed page. Static, no build step. | T04 |
| `seed/` | Seed instructions, test persona, the two dog photos | T04 |
| `screens/` | Recorded `ScreenSnapshot` JSON for offline agent tests | T01 |

## Test forms

| Form | Path | Fields, in order | Submit button |
|---|---|---|---|
| Library workshop | `/library/` | Session (radio ×2), Full name, Email, Phone, Confirmation method (Email / Text message) | Register |
| Volunteer signup | `/volunteer/` | Email address, Your name, Shift (radio ×2), Preferred volunteer role (select), Mobile number, How should we send your confirmation? (Text message / Phone call / Email) | Sign up for shift |

Both use plain accessible markup (`<label for>`, `<fieldset>`/`<legend>`, required fields with
visible "(required)") and contain nothing aimed at our agent. Missing fields show an error box
at the top. Submitting goes to `confirmed.html`, which shows a confirmation heading
("Registration confirmed" / "You're signed up"), a confirmation number and every submitted value.

Times come from the link's query string (`s1`, `s2` as local `YYYY-MM-DDTHH:MM`, `d` minutes);
without it they default to seed-day offsets (see `seed/README.md`).

## Run locally

```bash
cd fixtures/forms
python -m http.server 8080
```

Desktop: `http://localhost:8080/library/`. Emulator Chrome: `http://10.0.2.2:8080/library/`
(`10.0.2.2` is the host machine; it's already in the agent's form-host allowlist).

## Deployed

Not deployed yet. Planned: Vercel static site with `fixtures/forms` as the root directory. Once
deployed, put the URL here and in `vault/team-memory/decisions/YYYY-MM-DD-test-form-urls.md`,
and tell T03 to set `formHosts` in `AgentConfig.kt`.
