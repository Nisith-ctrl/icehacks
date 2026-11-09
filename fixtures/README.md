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

**https://saythegoal-forms-nisith.vercel.app** (Vercel project `saythegoal-forms`, account
`nisith-ctrl`, static files, no build step).

| Page | URL |
|---|---|
| Seed page (team only) | https://saythegoal-forms-nisith.vercel.app/seed/ |
| Library form | https://saythegoal-forms-nisith.vercel.app/library/ |
| Volunteer form | https://saythegoal-forms-nisith.vercel.app/volunteer/ |

- The production URL is public. Vercel's login wall applies only to preview deployments.
- **Not linked to GitHub** (Vercel's GitHub app can't see this private repo), so pushing changes
  to `fixtures/forms` does **not** redeploy. After merging a change, ask Nisith to redeploy, or run
  `npx vercel deploy --prod` from `fixtures/forms` while logged in to the `nisith-ctrl` account.
