# Devpost draft (for the team to review; a human submits)

## Name

SuuS: a voice agent that uses your phone for you

## Tagline (elevator pitch)

An Android voice agent for people who find small taps and typing painful. Say what you need, and SuuS uses your apps for you: it fills in forms, attaches photos, and asks before sending anything.

<!-- Paste everything from "Inspiration" to "About the video" into Devpost's "About the project" box. -->

## Inspiration

For a lot of people, the hard part of a phone isn't knowing what to do. It's the doing. Arthritis, tremors, low vision, or simply getting older can turn small taps, typing and tiny options into something slow and painful, even for people who are perfectly comfortable talking.

Voice assistants were supposed to help, but they stop right where it starts to hurt. They can open an app, set a timer or send a short text, and then they hand you back the screen for the form, the date picker or the attachment. Those are exactly the parts that take dozens of small, precise taps. We wanted an assistant that keeps going through that part, while you stay in charge of what actually gets sent.

## What it does

SuuS is an Android voice agent that uses your apps for you. You say "Suus, sign me up for the library workshop that fits my calendar," and it takes it from there. It finds the invite in Gmail, opens the signup link, notices that the first session clashes with the dentist, and fills in the form for the other one. When it needs something it doesn't know, it asks out loud, and if you say "remember that," it does, so the next form needs one question fewer.

Nothing is sent without you. SuuS reads everything back and waits for you to say "Submit" or "Send." That approval is tied to the exact screen you heard, so if anything changes, it asks again. Afterwards it reads the confirmation page and tells you your details came through. You can say "Stop" at any moment, and it stops within about a tenth of a second.

It can also pick up something you've already started. With an email to Maya half-written, "Suus, take over. Attach yesterday's dog photo" makes it find yesterday's photos and ask which one. If it's the wrong one, "Undo the photo" removes only the photo and leaves your words alone. The whole time, a small robot points at each control before it's tapped, so you can always see what SuuS is about to do.

## How we built it

SuuS is a Kotlin Android app in six modules: device control, voice, the agent, the on-screen robot, a small on-device database, and the app that ties them together. An accessibility service reads the screen as a tree of buttons and text and performs the taps, typing and scrolling.

The thinking is split on purpose. An LLM on Groq's free tier (`openai/gpt-oss-120b`) looks at the goal and the screen as text and picks one next step at a time. Everything that has to be exact is plain code: filling in form values, finding calendar clashes, checking confirmation pages, undoing exactly one edit, and locking Submit and Send to a SHA-256 fingerprint of the screen you approved. That split is why filling a form takes only three or four model calls.

Each turn, the model sees the screen as compact text, one line per control, with an id it can act on:

```
n7  EditText "Alex" hint="Full name" label="Full name" [edit]
n12 RadioButton "Thursday, Nov 12, 10:00" [check, checked]
n20 Button "Submit" [tap]
```

It answers with exactly one tool call, such as `tap`, `enter_text`, `fill_form`, `check_calendar` or `ask_user`. The approval lock sits below all of that, in the device layer, so no plan the model makes can skip it. A Submit or Send tap is refused unless your spoken approval matches the fingerprint of the screen as it is right now:

```kotlin
if (action.isCommit) {
    if (approval == null) return ActionResult.NotAllowed("commit tap needs an approval")
    if (approval.fingerprint != beforeFp) {
        return ActionResult.NotAllowed("approval does not match the current screen")
    }
}
```

To ship it, we built a signed release APK that carries no API key. It talks to a small relay on Vercel that holds the key as a secret, allows only SuuS's two models and rate-limits each address. A website explains the app, shows the results and links the download.

## Two layers of AI

SuuS uses AI at runtime, and we also used AI to build it, in a workflow we designed for a team of agents. Each of the four of us ran our own AI coding agent (Claude Code) on our own machine. The agents couldn't talk to each other, so we gave them a shared memory: an Obsidian notebook synced through GitHub every two minutes.

The lead wrote a project brief with fixed contracts (the interfaces between modules, the JSON shapes, the setting names) and cut the work into six task files along folder boundaries. Every agent read the brief, its task, and every decision and dead end before starting. Each one worked on its own branch and wrote what it learned back as new files, so nothing ever conflicted.

That produced 57 merged pull requests, 118 commits to the notebook, 23 agent session logs, 9 binding decisions and 5 dead ends. One example: guru's agent logged that real speech turned "Hey Goal" into "hey girl," so the agent never woke up. The next agent working on voice read that note and built the sound-alike matching for "Suus." The decisions and dead ends are public in our repo under `docs/team/notebook/`.

## Challenges we ran into

The hardest part wasn't the AI. It was seeing the screen reliably. Gmail keeps the inbox "visible" behind an open email, Calendar lays an invisible full-screen layer over its event list, and the emulator's Messages app hides its text box from accessibility entirely. And a photo SuuS had just removed stayed in Android's cached accessibility tree, so it still looked present until we cleared that cache before every screen read.

Some problems changed the plan. "Insert photo" in Gmail puts the picture inside the email text, where undo can't reach it, so SuuS always uses "Attach file." The emulator can't send photo texts at all, so the photo demo moved from Messages to a Gmail draft.

Then there were the surprises. On our first recording take, Gmail popped up a tip about emoji reactions that we'd never seen. SuuS's tap missed, it read the screen again, saw the email was already open, and tapped the signup link. Because it decides each step from what's on screen, not from recorded tap positions, the tip didn't derail it. Finally, Groq's free tier only allows a few requests a minute, which is why parts of our video are sped up and labelled.

## Accomplishments that we're proud of

All three demos run end to end with zero touches after setup: a library registration, a volunteer signup on a form SuuS had never seen, and a photo email with undo. Done by hand, the two signups took 53 and 44 touches, most of them tiny keyboard keys.

We're especially proud of selective undo working live in Gmail. SuuS removed only the photo, checked that the recipient, subject and text were unchanged, then attached the right one. And we shipped: a public website and a signed APK anyone can install, with no account or API key needed.

## What we learned

Accessibility trees don't always match what's on screen, and reading the screen reliably took more work than planning the next step. We also learned to let the AI decide what to do next and let code handle anything that has to be exact, which made SuuS both more reliable and cheaper to run.

Voice control for people who find tapping hard needs two opposite things. The wake word has to be forgiving, because the recognizer doesn't know the word "Suus" and writes it as "sous", "Seuss" or "Zeus", so SuuS matches it by sound:

```kotlin
// "suus", "sous", "Seuss", "Zeus", "zoos"... all count as the wake word
private val SUUS = Regex("^[sz](u|uu|oo|ou|ue|eu|ew|oe|ui)(s|ss|se|z|zz|ze|ce)s?$")
```

Confirmation has to be the opposite: strict. Only the exact words "Submit" and "Send" can commit anything, and "yes" or "sure" just gets the question again. And a shared notebook of decisions and dead ends let four people and their coding agents build in parallel without stepping on each other.

## What's next for SuuS

Next we want SuuS to handle more apps and forms beyond our test site, and to let people teach it a new task by showing it once. Most of all, we want to test it with people who have limited hand dexterity, on real phones with real speech. An iPhone version would need a different approach, because iOS doesn't let apps read and tap other apps the way Android's accessibility service does.

## About the video

Recorded on the Android emulator. The screen recorder held the PC's microphone, so each command was sent to SuuS as text the moment it was spoken, and the commands are voiced over. Everything after that, every tap, question, approval and spoken reply, is SuuS running for real. Waits are sped up and labelled.

## Built with

Kotlin, Android Accessibility API, Android SpeechRecognizer, Android TextToSpeech, Kotlin coroutines,
kotlinx.serialization, OkHttp, AndroidX (AppCompat, SQLite), Gradle, Groq (`openai/gpt-oss-120b`,
`qwen/qwen3.8-27b`), Vercel (website, test forms, API relay), Node.js (relay), HTML/CSS/JavaScript,
Motion (website animation), Google Fonts, **Claude Code** (AI coding assistant), **ChatGPT** (robot
artwork), Mobbin (design
references, IceHacks sponsor)

(For the Devpost "Built with" tags: kotlin, android, groq, okhttp, sqlite, gradle, vercel, node.js,
javascript, html5, css3, motion, claude-code, chatgpt, mobbin.)

## AI tools (disclosure, required by the rules)

All four of us used **Claude Code** (Anthropic; Claude Opus 5.5 and Claude Fable models) as an AI
coding assistant during the hackathon, to write and test much of the Kotlin code, the website and the
docs. Commits it helped with carry a `Co-Authored-By: Claude` trailer. The team designed the
product, the architecture and the safety rules, planned the tasks, made and recorded the decisions,
tested every demo on the emulator, and reviewed each pull request before merging. At runtime, SuuS
uses Groq-hosted models (`openai/gpt-oss-120b` to plan each step, `qwen/qwen3.8-27b` to describe
photos). The SuuS robot sprite and pixel speech-bubble style were generated with **ChatGPT** (OpenAI's
image generation) from Guru's prompts; we cut the sprite sheet into frames and built the overlay
around it. Everything was built during IceHacks (Nov 8–15); there's no pre-existing code.

## Credits

Open-source libraries listed above; Atkinson Hyperlegible and Pixelify Sans fonts (SIL OFL); website
fonts from Google Fonts; design references from Mobbin; layout ideas from stripe.com (inspiration
only). The robot sprite and pixel speech bubble were generated with ChatGPT. Test forms, emails and photos
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
  Recorded on the Android emulator. The screen recorder held the PC's microphone, so in the
  recording each command was sent to SuuS as text the moment it was spoken (SuuS's built-in test
  hook), and the commands are voiced over. Everything after that (reading the screen, every tap,
  the questions, the approvals, the spoken replies) is SuuS running for real. Waits are sped up and
  labelled. With a free microphone, SuuS takes the same commands by voice.
- Website: https://suus-nisith.vercel.app
- Download: https://github.com/Nisith-ctrl/icehacks/releases/tag/v1.0
- Code: https://github.com/Nisith-ctrl/icehacks
- Setup: `docs/setup.md`. Tested environments: `docs/tested-environments.md`. Measurements: `docs/metrics.md`.
