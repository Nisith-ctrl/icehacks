package com.icehacks.suus.agent

import kotlinx.serialization.json.JsonObject

/** Everything the model sees on one turn. Each turn is a fresh, self-contained request. */
data class PlannerTurn(
    val goal: String,
    val takeOver: Boolean,
    val now: String,
    val preferences: List<String>,
    val notes: List<String>,
    val history: List<String>,
    val feedback: String?,
    val screen: String,
    /** Summary of the web form on screen (fields, what fills them, submit button), if any. */
    val form: String? = null,
)

/** One tool call chosen by the model. [tool] is "none" if it answered without a tool. */
data class PlannerStep(val tool: String, val input: JsonObject, val text: String? = null)

interface Planner {
    suspend fun next(turn: PlannerTurn): PlannerStep

    /** Short visual description of a photo (for "the one by the door"). Null if unavailable. */
    suspend fun describePhoto(jpeg: ByteArray): String? = null
}

/** Replays scripted steps; records the turns it was given. For offline tests. */
class FakePlanner(steps: List<PlannerStep>, private val photoDescriptions: List<String> = emptyList()) : Planner {
    private val queue = ArrayDeque(steps)
    private val photos = ArrayDeque(photoDescriptions)
    val turns = mutableListOf<PlannerTurn>()

    override suspend fun next(turn: PlannerTurn): PlannerStep {
        turns += turn
        return queue.removeFirstOrNull() ?: PlannerStep("stop", tool("reason" to "script ended"))
    }

    override suspend fun describePhoto(jpeg: ByteArray): String? = photos.removeFirstOrNull()
}

fun tool(vararg pairs: Pair<String, Any?>): JsonObject = kotlinx.serialization.json.buildJsonObject {
    for ((k, v) in pairs) when (v) {
        null -> {}
        is String -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
        is Number -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
        is Boolean -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
        is kotlinx.serialization.json.JsonElement -> put(k, v)
        is List<*> -> put(k, kotlinx.serialization.json.JsonArray(v.map {
            when (it) {
                is kotlinx.serialization.json.JsonElement -> it
                else -> kotlinx.serialization.json.JsonPrimitive(it.toString())
            }
        }))
        else -> put(k, kotlinx.serialization.json.JsonPrimitive(v.toString()))
    }
}
