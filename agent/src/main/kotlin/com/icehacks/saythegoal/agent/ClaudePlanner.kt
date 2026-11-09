package com.icehacks.saythegoal.agent

import android.util.Base64
import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.fasterxml.jackson.databind.JsonNode
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolChoiceAuto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Duration

/**
 * Claude via the official Java SDK. One stateless request per turn: cached system prompt + tools,
 * then a user message with the goal, notes, recent steps and the current screen as text.
 * Low effort for latency; at most one tool call per turn; server-side refusal fallback enabled.
 */
class ClaudePlanner(private val config: AgentConfig) : Planner {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(config.apiKey)
        .timeout(Duration.ofSeconds(45))
        .maxRetries(2)
        .build()

    private val system = listOf(
        TextBlockParam.builder()
            .text(AgentPrompt.system(config.profile))
            .cacheControl(CacheControlEphemeral.builder().build())
            .build(),
    )

    private val tools: List<Tool> = AgentTools.all.map { spec ->
        Tool.builder()
            .name(spec.name)
            .description(spec.description)
            .inputSchema(
                Tool.InputSchema.builder()
                    .properties(
                        Tool.InputSchema.Properties.builder().apply {
                            spec.properties.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
                        }.build(),
                    )
                    .required(spec.required)
                    .build(),
            )
            .build()
    }

    override suspend fun next(turn: PlannerTurn): PlannerStep = withContext(Dispatchers.IO) {
        val params = base(1_500)
            .apply { tools.forEach { addTool(it) } }
            .toolChoice(ToolChoiceAuto.builder().disableParallelToolUse(true).build())
            .addUserMessage(AgentPrompt.user(turn))
            .build()
        val started = System.currentTimeMillis()
        val response = call(params) ?: return@withContext PlannerStep("stop", tool("reason" to "I couldn't reach my planning service."))
        Log.i(TAG, "turn ${System.currentTimeMillis() - started} ms, in=${response.usage().inputTokens()} " +
            "cached=${response.usage().cacheReadInputTokens().orElse(0)} out=${response.usage().outputTokens()}")

        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            return@withContext PlannerStep("stop", tool("reason" to "I can't help with that one."))
        }
        val use = response.content().firstNotNullOfOrNull { it.toolUse().orElse(null) }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString(" ").ifBlank { null }
        if (use == null) return@withContext PlannerStep("none", JsonObject(emptyMap()), text)
        val input = Json.parseToJsonElement(use._input().convert(JsonNode::class.java).toString()).jsonObject
        PlannerStep(use.name(), input, text)
    }

    override suspend fun describePhoto(jpeg: ByteArray): String? = withContext(Dispatchers.IO) {
        val image = ImageBlockParam.builder()
            .source(
                Base64ImageSource.builder()
                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                    .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                    .build(),
            )
            .build()
        val params = base(200)
            .addUserMessageOfContentBlockParams(
                listOf(
                    ContentBlockParam.ofImage(image),
                    ContentBlockParam.ofText("Describe where this photo was taken and its main subject in at most 8 words, e.g. \"dog in the backyard grass\". Reply with the words only."),
                ),
            )
            .build()
        val r = call(params) ?: return@withContext null
        r.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString(" ").trim().ifBlank { null }
    }

    private fun base(maxTokens: Long): MessageCreateParams.Builder = MessageCreateParams.builder()
        .model(config.model)
        .maxTokens(maxTokens)
        .systemOfTextBlockParams(system)
        .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
        // Server-side refusal fallback (Claude API): routes a refused request to a fallback model.
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    private fun call(params: MessageCreateParams): Message? = try {
        client.messages().create(params)
    } catch (e: AnthropicServiceException) {
        Log.w(TAG, "Claude API error ${e.statusCode()}: ${e.message}")
        null
    } catch (e: Exception) {
        Log.w(TAG, "Claude request failed", e)
        null
    }

    companion object { const val TAG = "STG-Agent" }
}
