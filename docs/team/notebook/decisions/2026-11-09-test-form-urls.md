---
date: 2026-11-09
author: Nisith
task: T04
status: accepted
---

# Test form URLs

## Decision

The test forms are deployed at **https://saythegoal-forms-nisith.vercel.app**:

- Library form: `/library/`, and its confirmation page `/library/confirmed.html`
- Volunteer form: `/volunteer/`, and its confirmation page `/volunteer/confirmed.html`
- Seed page (team only, the agent never visits it): `/seed/`

Invite emails must be generated from the deployed `/seed/` page so their links point here.

## Why

The emulator and the S21 both need to reach the forms without the laptop's local server.

## Alternatives considered

- Local `python -m http.server` (`http://10.0.2.2:8080/` from the emulator): still works for
  development, but the S21 can't reach it.
- Linking the Vercel project to GitHub for automatic deploys: Vercel's GitHub app can't see
  the private repo, so deploys are manual for now (see `fixtures/README.md`).

## Affects

- [T03](../README.md#task-board): `formHosts` in `AgentConfig.kt` currently allows any `vercel.app` host.
  Narrow it to `saythegoal-forms-nisith.vercel.app` (keep `10.0.2.2` and `localhost` for local runs).
- [T04](../README.md#task-board): redeploy after any change to `fixtures/forms`.
