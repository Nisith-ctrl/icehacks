package com.icehacks.suus.agent

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Groq's OpenAI-compatible Chat Completions API (free tier), over plain OkHttp.
 * One stateless request per turn (system prompt + one user message with the goal, notes, recent
 * steps and the screen as text), one tool call back.
 *
 * Free-tier limits are tight (about 30 requests and 8K tokens per minute, 200K tokens per day),
 * so requests are kept small and a 429 waits for `retry-after` and tries again.
 * Optional request fields Groq may reject (`tool_choice: required`, `reasoning_effort`) are
 * dropped automatically after a 400 that names them.
 */
class GroqPlanner(
    private val config: AgentConfig,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val log: (String) -> Unit = { runCatching { Log.i(TAG, it) } },
) : Planner {

    @Volatile private var useRequiredToolChoice = true
    @Volatile private var useReasoningEffort = true

    private val tools: JsonArray = buildJsonArray {
        for (spec in AgentTools.all) add(buildJsonObject {
            put("type", "function")
            put("function", buildJsonObject {
                put("name", spec.name)
                put("description", spec.description)
                put("parameters", buildJsonObject {
                    put("type", "object")
                    put("properties", toJson(spec.properties.mapValues { (name, schema) -> if (name in spec.required) schema else nullable(schema) }))
                    put("required", toJson(spec.required))
                })
            })
        })
    }

    /** Rebuilt each turn, so details saved on the "About you" screen are used straight away. */
    private val system get() = AgentPrompt.system(config.currentProfile())

    override suspend fun next(turn: PlannerTurn): PlannerStep = withContext(Dispatchers.IO) {
        repeat(3) {
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", buildJsonArray {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", AgentPrompt.user(turn)) })
                })
                put("tools", tools)
                put("tool_choice", if (useRequiredToolChoice) "required" else "auto")
                put("parallel_tool_calls", false)
                if (useReasoningEffort) put("reasoning_effort", "low")
                put("max_completion_tokens", 1024)
            }
            when (val r = post(body)) {
                is Reply.Ok -> return@withContext parse(r.json)
                is Reply.Retry -> {}
                is Reply.Fail -> return@withContext PlannerStep("stop", tool("reason" to r.spoken))
                is Reply.BadToolCall -> return@withContext PlannerStep("none", JsonObject(emptyMap()),
                    "Your last tool call was malformed (${r.detail}). Call exactly one tool with valid JSON arguments.")
            }
        }
        PlannerStep("stop", tool("reason" to "My planning service keeps failing, so I stopped."))
    }

    override suspend fun describePhoto(jpeg: ByteArray): String? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", config.visionModel)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "Describe where this photo was taken and its main subject in at most 8 words, e.g. \"dog in the backyard grass\". Reply with the words only.")
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))
                            })
                        })
                    })
                })
            })
            put("max_completion_tokens", 60)
        }
        repeat(2) {
            when (val r = post(body)) {
                is Reply.Ok -> return@withContext message(r.json)?.get("content")?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
                is Reply.Retry -> {}
                else -> return@withContext null
            }
        }
        null
    }

    // ---------- HTTP ----------

    private sealed interface Reply {
        data class Ok(val json: JsonObject) : Reply
        data object Retry : Reply
        data class Fail(val spoken: String) : Reply
        data class BadToolCall(val detail: String) : Reply
    }

    private suspend fun post(body: JsonObject): Reply {
        val req = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()
        val started = System.currentTimeMillis()
        val (code, text, retryAfter) = try {
            http.newCall(req).execute().use { Triple(it.code, it.body?.string().orEmpty(), it.header("retry-after")) }
        } catch (e: Exception) {
            log("request failed: ${e.message}")
            return Reply.Fail("I couldn't reach my planning service.")
        }
        val ms = System.currentTimeMillis() - started
        when {
            code == 200 -> {
                val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: return Reply.Fail("My planning service sent something I couldn't read.")
                val usage = json["usage"] as? JsonObject
                log("turn $ms ms, model=${body["model"]?.jsonPrimitive?.content}, in=${usage?.get("prompt_tokens")} out=${usage?.get("completion_tokens")}")
                return Reply.Ok(json)
            }
            code == 429 -> {
                val wait = retryAfter?.toDoubleOrNull()?.toLong() ?: 20
                log("rate limited, retry-after=$retryAfter: ${text.take(240).replace("\n", " ")}")
                if (wait > config.maxRateLimitWaitSec) return Reply.Fail("I've hit the free usage limit. Please try again later.")
                sleep(wait * 1000 + 250)
                return Reply.Retry
            }
            code == 401 -> return Reply.Fail("My planning service rejected the API key. Check GROQ_API_KEY.")
            code == 400 -> {
                val err = errorOf(text)
                log("400: $err")
                when {
                    err.code == "tool_use_failed" -> return Reply.BadToolCall(err.message.take(200))
                    useReasoningEffort && err.mentions("reasoning_effort") -> { useReasoningEffort = false; return Reply.Retry }
                    useRequiredToolChoice && err.mentions("tool_choice") -> { useRequiredToolChoice = false; return Reply.Retry }
                    else -> return Reply.Fail("My planning service rejected the request.")
                }
            }
            code >= 500 -> { sleep(1_500); return Reply.Retry }
            else -> {
                log("HTTP $code: ${text.take(300)}")
                return Reply.Fail("My planning service returned an error.")
            }
        }
    }

    private data class ApiError(val code: String?, val message: String) {
        fun mentions(field: String) = message.contains(field, ignoreCase = true) || code?.contains(field) == true
    }

    private fun errorOf(text: String): ApiError {
        val e = runCatching { Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject }.getOrNull()
        return ApiError(e?.get("code")?.jsonPrimitive?.contentOrNull, e?.get("message")?.jsonPrimitive?.contentOrNull ?: text.take(300))
    }

    // ---------- response ----------

    private fun message(json: JsonObject): JsonObject? =
        (json["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message")?.jsonObject

    private fun parse(json: JsonObject): PlannerStep {
        val msg = message(json) ?: return PlannerStep("none", JsonObject(emptyMap()), null)
        val text = msg["content"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.ifBlank { null }
        val call = (msg["tool_calls"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("function")?.jsonObject
            ?: return PlannerStep("none", JsonObject(emptyMap()), text)
        val name = call["name"]?.jsonPrimitive?.contentOrNull ?: return PlannerStep("none", JsonObject(emptyMap()), text)
        val args = call["arguments"]?.let { a ->
            when {
                a is JsonObject -> a
                else -> runCatching { Json.parseToJsonElement(a.jsonPrimitive.content).jsonObject }.getOrNull()
            }
        } ?: JsonObject(emptyMap())
        return PlannerStep(name, args, text)
    }

    companion object {
        const val TAG = "SUUS-Agent"
        private val JSON = "application/json".toMediaType()

        /** Lets an optional parameter also be null: models often send null for "not given". */
        fun nullable(schema: Map<String, Any>): Map<String, Any> {
            val t = schema["type"] as? String ?: return schema
            return schema + ("type" to listOf(t, "null"))
        }

        fun toJson(v: Any?): JsonElement = when (v) {
            null -> JsonNull
            is JsonElement -> v
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
            is Iterable<*> -> JsonArray(v.map { toJson(it) })
            else -> JsonPrimitive(v.toString())
        }
    }
}
