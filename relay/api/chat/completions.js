// SuuS relay: the downloadable app talks to this, and this talks to Groq with our key.
// The real Groq key lives only here, as the GROQ_API_KEY environment variable on Vercel, so it is
// never inside the APK or on anyone's phone.
//
// It only does what SuuS needs, so the key can't be borrowed for anything else:
//   - POST /api/chat/completions only (the same shape as Groq's OpenAI-compatible API)
//   - only SuuS's two models, small replies, at most a few images
//   - the app's pass (SUUS_APP_TOKEN, sent as "Authorization: Bearer ...") must match
//   - a per-address rate limit (per warm instance)
// Environment variables (set in the Vercel project, never in the repo):
//   GROQ_API_KEY    the real key
//   SUUS_APP_TOKEN  the pass the release APK sends (not secret on its own; it just keeps out scanners)

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const ALLOWED_MODELS = new Set(["openai/gpt-oss-120b", "qwen/qwen3.8-27b"]);
const MAX_COMPLETION_TOKENS = 1024;
const MAX_MESSAGES = 6;
const PER_MINUTE = 40; // SuuS makes about 3–10 calls per command

const hits = new Map(); // ip -> [timestamps in the last minute]

export default async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store");
  if (req.method !== "POST") return deny(res, 405, "Use POST.");

  const pass = process.env.SUUS_APP_TOKEN;
  const auth = String(req.headers.authorization || "");
  if (!pass || auth !== `Bearer ${pass}`) return deny(res, 401, "Unknown app.");
  if (!process.env.GROQ_API_KEY) return deny(res, 500, "Relay is missing its key.");

  const ip = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim() || "unknown";
  const now = Date.now();
  const recent = (hits.get(ip) || []).filter((t) => now - t < 60_000);
  if (recent.length >= PER_MINUTE) {
    res.setHeader("retry-after", "20");
    return deny(res, 429, "Too many requests from this device. Try again in a moment.");
  }
  recent.push(now);
  hits.set(ip, recent);
  if (hits.size > 5000) hits.clear(); // keep memory bounded

  let body = req.body;
  if (typeof body === "string") {
    try { body = JSON.parse(body); } catch { return deny(res, 400, "Body must be JSON."); }
  }
  if (!body || typeof body !== "object") return deny(res, 400, "Body must be JSON.");
  if (!ALLOWED_MODELS.has(body.model)) return deny(res, 400, "Model not allowed.");
  if (!Array.isArray(body.messages) || body.messages.length === 0 || body.messages.length > MAX_MESSAGES) {
    return deny(res, 400, "Bad messages.");
  }
  delete body.stream;
  delete body.n;
  const asked = Number(body.max_completion_tokens || body.max_tokens || MAX_COMPLETION_TOKENS);
  body.max_completion_tokens = Math.min(Number.isFinite(asked) ? asked : MAX_COMPLETION_TOKENS, MAX_COMPLETION_TOKENS);
  delete body.max_tokens;

  let upstream;
  try {
    upstream = await fetch(GROQ_URL, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${process.env.GROQ_API_KEY}` },
      body: JSON.stringify(body),
    });
  } catch {
    return deny(res, 502, "Couldn't reach the AI service.");
  }

  // Pass Groq's answer through as-is, including its rate-limit hint, which the app already handles.
  const retryAfter = upstream.headers.get("retry-after");
  if (retryAfter) res.setHeader("retry-after", retryAfter);
  res.status(upstream.status);
  res.setHeader("content-type", upstream.headers.get("content-type") || "application/json");
  return res.send(await upstream.text());
}

function deny(res, status, message) {
  return res.status(status).json({ error: { message, type: "suus_relay" } });
}
