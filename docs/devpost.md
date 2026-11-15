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
  the invite in Gmail, opens the link, checks Calendar (the first session clashes with the
  dentist, so it picks the other one), fills the form, and reads everything back.
- **You approve, by voice:** nothing is submitted or sent until you say "Submit" or "Send". If the
  screen changed after you approved, it asks again.
- **It checks the result:** after submitting it reads the confirmation page and tells you the
  confirmation number, and that all your details came through.
- **You choose what it remembers:** "use Email for event signups" is saved once and reused on the
  next form. "What do you remember?" and "Forget …" work by voice, and on a big-button screen.
- **Hand off what you started:** with an email to Maya half-written in Gmail, "Suus, take over.
  Attach yesterday's dog photo." SuuS describes both dog photos and asks which one. "Undo the photo"
  removes only the photo and confirms your words are untouched; "Use the one by the door" attaches the
  other one; "Send it" reads everything back and waits for you to say "Send".
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
- Shipping: a signed release APK (SuuS 1.0) on GitHub Releases. It carries no API key: release builds
  call a small relay on Vercel (`relay/`) that holds the Groq key as a secret, allows only SuuS's two
  models, and rate-limits each address. A one-page website explains the app and links the download.

## Challenges

- Apps hide things from accessibility: Gmail keeps the inbox "visible" behind an open email,
  Calendar lays an invisible full-screen layer over its event list, and older Messages builds
  hid the text box entirely.
- Gmail attachments: "Insert photo" puts the picture inside the email text, where undo can't remove
  it, so SuuS always uses "Attach file". The attachment card appears a moment late, and a removed one
  stayed in the accessibility tree until we cleared the cache before every screen read.
- The emulator can't send photo texts (no MMS), so the photo demo moved from Messages to a Gmail draft.
- Timing: cold app starts and slow first launches look like "nothing happened" unless you wait carefully.
- Free-tier rate limits: small, stateless prompts, code doing the exact parts (3–4 model calls to fill a
  form, 8–11 for a whole run from the home screen), and automatic waits. The waits are why parts of our video are sped up and labelled;
  a paid API tier removes most of them.

## Accomplishments

- All three demos run end to end on the emulator with **zero touches** after setup (see `docs/metrics.md`):
  - library registration: 2 spoken commands, confirmed LIB-282497
  - volunteer signup, on a form it had never seen: 3 spoken commands, confirmed VOL-730060
  - photo email with undo: 6 spoken commands, sent
- The same two signups by hand took **53 and 44 touches**, most of them tiny keyboard keys.
- Selective undo works live in Gmail: SuuS attached the yard photo, removed only that photo, checked the
  recipient, subject and text were unchanged, then attached the one by the door.
- Shipped: a public website and a downloadable, signed APK that needs no API key from the user.

## What we learned

<!-- team: edit or replace these drafts, and add your own line -->
- Accessibility trees don't always match what's on screen: apps keep hidden layers "visible" and
  cache removed items. Reading the screen reliably took more work than planning the next step.
- Let the AI decide what to do next, and let code do anything that must be exact. That made it
  both more reliable and cheaper: 3–4 model calls to fill a form.
- Voice for older users needs forgiving matching ("Suus" is often heard as "sis" or "cease") and
  strict confirmation: only the exact words "Submit" and "Send" can commit.
- A shared notebook of decisions and dead ends let four people and their coding agents build in
  parallel without stepping on each other.

## What's next

- More apps and forms, beyond our test site; letting users teach a new workflow by showing it once.
- On-device speech recognition, and testing on real phones with real speech.
- Testing with people who have limited hand dexterity.
- An iPhone version. iOS doesn't let apps read and tap other apps the way Android's accessibility
  service does, so it would need a different approach.
- A paid or faster model tier to remove the rate-limit waits.

## Built with

Kotlin, Android Accessibility API, Android SpeechRecognizer, Android TextToSpeech, Kotlin coroutines,
kotlinx.serialization, OkHttp, AndroidX (AppCompat, SQLite), Gradle, Groq (`openai/gpt-oss-120b`,
`qwen/qwen3.8-27b`), Vercel (website, test forms, API relay), Node.js (relay), HTML/CSS/JavaScript,
Motion (website animation), Google Fonts, **Claude Code** (AI coding assistant), Mobbin (design
references, IceHacks sponsor)

(For the Devpost "Built with" tags: kotlin, android, groq, okhttp, sqlite, gradle, vercel, node.js,
javascript, html5, css3, motion, claude-code, mobbin.)

## AI tools (disclosure, required by the rules)

All four of us used **Claude Code** (Anthropic; Claude Opus 5.5 and Claude Fable models) as an AI
coding assistant during the hackathon, to write and test much of the Kotlin code, the website and the
docs. Commits it helped with carry a `Co-Authored-By: Claude` trailer. The team designed the
product, the architecture and the safety rules, planned the tasks, made and recorded the decisions,
tested every demo on the emulator, and reviewed each pull request before merging. At runtime, SuuS
uses Groq-hosted models (`openai/gpt-oss-120b` to plan each step, `qwen/qwen3.8-27b` to describe
photos). Everything was built during IceHacks (Nov 8–15); there's no pre-existing code.

## Credits

Open-source libraries listed above; Atkinson Hyperlegible and Pixelify Sans fonts (SIL OFL); website
fonts from Google Fonts; design references from Mobbin; layout ideas from stripe.com (inspiration
only). The robot sprite and pixel speech bubble were supplied by Guru. Test forms, emails and photos
are our own fixtures.

## Try it

1. Download **SuuS 1.0** (Android 11+, about 10 MB):
   https://github.com/Nisith-ctrl/icehacks/releases/latest/download/SuuS-1.0.apk
2. Open it and allow installs from your browser if asked.
3. When you turn SuuS on in Accessibility settings, Android may first ask you to allow restricted
   settings (SuuS's App info → ⋮ → Allow restricted settings).
4. Follow the setup screen, then say "Suus".

Limits worth knowing: Android only (no iPhone yet), tested on the Android 15 emulator, forms are
only filled on our test site, and everyone using the APK shares one free-tier AI limit, so it may
say it has hit the usage limit.

## Tracks

- Overall
- Best AI Hack
- Best Design & UX (accessibility is the core of the project)
- Best Deployed / Shipped App: a public website and a signed, downloadable APK, with setup docs
  <!-- team: confirm we're entering this one -->

## Links

- Video: <!-- add the YouTube link -->
- Website: https://suus-nisith.vercel.app
- Download: https://github.com/Nisith-ctrl/icehacks/releases/tag/v1.0
- Code: https://github.com/Nisith-ctrl/icehacks
- Setup: `docs/setup.md`. Tested environments: `docs/tested-environments.md`. Measurements: `docs/metrics.md`.
