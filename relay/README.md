# SuuS relay

The downloadable SuuS app talks to this relay, and the relay talks to Groq with the team's key. The
key only lives here, as a secret environment variable on Vercel, so it is never inside the APK.

One function: `POST /api/chat/completions`, the same shape as Groq's OpenAI-compatible API. It only
allows SuuS's two models and small replies, checks the app's pass, and rate-limits each address.

## Deploy (Vercel, free tier)

1. Import this repo in Vercel and set **Root Directory** to `relay` (or run `vercel` inside `relay/`).
2. In the project's **Settings → Environment Variables**, add:
   - `GROQ_API_KEY`: the Groq key (from console.groq.com). **Never commit it.**
   - `SUUS_APP_TOKEN`: the pass the release APK sends. It's the same value as `SUUS_RELAY_TOKEN` in
     the builder's `local.properties`. It isn't a real secret (anyone could pull it out of the APK);
     it just keeps out random scanners.
3. Redeploy, so the variables take effect.

## Build the APK against it

In `local.properties` (gitignored):

```
SUUS_RELAY_URL=https://<your-relay>.vercel.app/api
SUUS_RELAY_TOKEN=<same as SUUS_APP_TOKEN>
```

Release builds (`./gradlew :app:assembleRelease`) then use the relay and carry no Groq key. Debug
builds still call Groq directly with `GROQ_API_KEY` from `local.properties`.

## Check it

```bash
curl -s -X POST "$SUUS_RELAY_URL/chat/completions" \
  -H "Authorization: Bearer $SUUS_RELAY_TOKEN" -H "content-type: application/json" \
  -d '{"model":"openai/gpt-oss-120b","messages":[{"role":"user","content":"Say hi"}],"max_completion_tokens":20}'
```

Everyone shares the key's free-tier limits (8K tokens a minute, 200K a day). If it runs out, SuuS
says it's hit the usage limit.
