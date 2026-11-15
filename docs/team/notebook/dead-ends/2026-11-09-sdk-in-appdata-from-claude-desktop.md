---
date: 2026-11-09
author: Susmith (T01 agent)
task: T01
---

# Installing the Android SDK into AppData from the Claude desktop app

## What we tried

Letting the agent (running inside the Claude desktop app on Windows) install the Android SDK with
`sdkmanager` into the default `%LOCALAPPDATA%\Android\Sdk`.

## What happened

The desktop app is sandboxed: its writes to `AppData\Local` are silently redirected to
`AppData\Local\Packages\Claude_…\LocalCache\Local\…`. Gradle started by the agent sees the SDK,
but Android Studio and your own terminal don't.

Also: `sdkmanager.bat` called from PowerShell/Git Bash can split `system-images;android-35;…` at
the `;` and report "Package … not found". `--licenses` fed from PowerShell didn't record licenses either.

## Do instead

- Put the SDK outside AppData, e.g. `C:\Users\<you>\Android\Sdk`, and set
  `sdk.dir=C:/Users/<you>/Android/Sdk` in `local.properties` (forward slashes).
- Install packages with `sdkmanager --package_file=<file with one package per line>`, and accept
  licenses from Git Bash with `yes | sdkmanager.bat --licenses`.
- Or just let Android Studio's first-run wizard install everything.
