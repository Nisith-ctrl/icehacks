---
date: 2026-11-09
author: Susmith (T03)
task: T03
status: accepted
---

# The agent uses Groq (free tier), not the Anthropic API

## Decision

- The planner calls **Groq's OpenAI-compatible Chat Completions API**
  (`https://api.groq.com/openai/v1/chat/completions`) over OkHttp. The `anthropic-java` SDK is removed.
- Models: **`openai/gpt-oss-120b`** plans the steps (production, tool calling). **`qwen/qwen3.8-27b`**
  describes photos for Demo 2 (vision; still a preview model).
- Config in `local.properties` (gitignored): **`GROQ_API_KEY`** (required; free at
  console.groq.com), optional `GROQ_MODEL` and `GROQ_VISION_MODEL`. **`ANTHROPIC_API_KEY` is no
  longer used.** Never put the key in git or the vault.
- This replaces the "AI" line of [2026-11-09-architecture-and-module-split](2026-11-09-architecture-and-module-split.md) and the
  `ANTHROPIC_API_KEY` row in the `project-brief` (private notes) env table. The rest of that decision stands
  (one model call per step, code validates and executes).

## Why

The team doesn't want a paid API. Groq has a free tier with tool calling and an image model.

## Alternatives considered

- Anthropic (Claude Sonnet 5.5): better tool use, but paid.
- xAI Grok: usually needs paid credits.
- Llama 3.x on Groq: shut down on 2026-08-16.

## Affects

- [T03](../README.md#task-board) (done, PR https://github.com/Nisith-ctrl/icehacks/pull/9).
- Nisith: please update the brief's env table and the assignments ("share `ANTHROPIC_API_KEY`")
  to `GROQ_API_KEY`. Each person can get their own free key, but limits are per Groq organization.
- Everyone running the agent: **free-tier limits are tight** (about 30 requests and 8K tokens
  per minute, 200K tokens per day). One agent step is about 3–5K tokens, so expect about 2 steps a
  minute. Groq's pay-as-you-go plan raises this to 250K tokens/min if recording needs it. See `groq-api` (private notes).
