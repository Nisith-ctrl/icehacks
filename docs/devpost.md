# Devpost draft (for the team to review; a human submits)

## Name

SuuS: a voice agent that does the tapping for you

## Tagline

Say what you need. SuuS shows you every tap, asks before anything is sent, and undoes just the part you didn't want.

## Inspiration

Alex Rivera has arthritis in both hands. Small taps, typing and picking tiny options hurt, but
talking is easy. Alex signs up for library workshops, volunteers at local events and texts family
to say they got home. Voice assistants can open an app, but they stop at the part that hurts:
the form, the date picker, the attachment.

## What it does

- **Say the goal:** "Suus, sign me up for the library workshop that fits my calendar." SuuS finds
  the invite in Gmail, opens the link, checks Calendar (the Thursday session clashes with the
  dentist, so it picks Friday), fills the form, and reads everything back.
- **You approve, by voice:** nothing is submitted or sent until you say "Submit" or "Send". If the
  screen changed after you approved, it asks again.
- **It checks the result:** after submitting it reads the confirmation page and tells you the
  confirmation number, and that all your details came through.
- **You choose what it remembers:** "use Email for event signups" is saved once and reused on the
  next form. "What do you remember?" and "Forget …" work by voice, and on a big-button screen.
- **Hand off what you started:** with an email to Maya half-written in Gmail, "Suus, take over.
  Attach yesterday's dog photo." It finds yesterday's photos, asks which one, and attaches it. "Undo
  the photo" removes only the photo and leaves your words alone, "Use the one by the door" swaps
  it, and it sends only after you hear the read-back and say "Send".
- **See what it's doing:** a small robot points at each control before tapping it, with a short caption.
- **"Stop" works any time**: once SuuS hears it, the current action is cancelled at once and it says "Stopped." in about a tenth of a second.

## How we built it

- Android app in Kotlin, built as modules: device control (an accessibility service that reads the
  screen and acts), voice (Android speech recognition, a sound-alike wake word "Suus", spoken
  approvals), the agent loop, the on-screen character, and an on-device database for preferences
  and a run log.
- Planning by an LLM on Groq's free tier (`openai/gpt-oss-120b`): each turn it sees the goal and
  the screen as text and calls one tool. Code, not the model, does the parts that must be exact:
  filling fields, matching calendar clashes, gating Submit/Send behind a fingerprinted spoken
  approval, verifying confirmation pages, and undoing exactly one edit.
- Safety: an app allowlist, forms only on our test site, and every commit action needs spoken approval.

## Challenges

- Apps hide things from accessibility: Gmail keeps the inbox "visible" behind an open email,
  Calendar lays an invisible full-screen layer over its event list, and older Messages builds
  hid the text box entirely.
- Timing: cold app starts and slow first launches look like "nothing happened" unless you wait carefully.
- Free-tier rate limits: small, stateless prompts, code doing the exact parts (3–4 model calls to fill a
  form, 8–11 for a whole run from the home screen), and automatic waits. The waits are why parts of our video are sped up and labelled;
  a paid API tier removes most of them.

## Accomplishments

- Library registration and volunteer signup run end to end with **zero touches** after setup:
  2 and 3 spoken commands, with verified confirmations (see `docs/metrics.md`).

## What we learned

<!-- team: add yours -->

## What's next

More apps and forms; on-device speech; letting users teach new workflows by showing them once.

## Built with

Kotlin, Android Accessibility API, Android SpeechRecognizer, Groq, SQLite, Vercel (test forms)

## Links

- Video: <!-- add -->
- Code: https://github.com/Nisith-ctrl/icehacks
- Setup: `docs/setup.md`. Tested environments: `docs/tested-environments.md`.
