package com.icehacks.suus.agent

/** Tool definitions given to the model: name, description, JSON-schema properties, required. */
data class ToolSpec(
    val name: String,
    val description: String,
    val properties: Map<String, Map<String, Any>>,
    val required: List<String>,
)

object AgentTools {
    private fun str(desc: String) = mapOf("type" to "string", "description" to desc)
    private val caption = str("Short on-screen caption in plain words, e.g. \"Opening Gmail\".")

    val all = listOf(
        ToolSpec("tap", "Tap a node on the current screen (not for Submit/Send: use submit).",
            mapOf("node_id" to str("Node id from the current screen."), "caption" to caption,
                "edit_label" to str("Only if this tap changes the draft (e.g. attaches a photo, picks an option): what it changes, e.g. \"dog photo (yard)\".")),
            listOf("node_id", "caption")),
        ToolSpec("enter_text", "Replace the text of an editable field.",
            mapOf("node_id" to str("Editable node id."), "text" to str("The full text the field should contain."), "caption" to caption),
            listOf("node_id", "text", "caption")),
        ToolSpec("scroll", "Scroll the screen or a scrollable node.",
            mapOf("direction" to mapOf("type" to "string", "enum" to listOf("up", "down")),
                "node_id" to str("Optional scrollable node id; omit for the main list."), "caption" to caption),
            listOf("direction", "caption")),
        ToolSpec("back", "Press the system back button.", mapOf("caption" to caption), listOf("caption")),
        ToolSpec("launch_app", "Open one of the allowed apps.",
            mapOf("app" to mapOf("type" to "string", "enum" to LAUNCHABLE_APPS.keys.toList()), "caption" to caption),
            listOf("app", "caption")),
        ToolSpec("ask_user", "Ask the user a short spoken question and get their answer.",
            mapOf("question" to str("The question, spoken aloud. Short."),
                "choices" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "The options to offer, if any, using the form's own wording."),
                "remember_scope" to str("If the answer might be saved as a preference, the kind of task it applies to, e.g. \"event_registration\"."),
                "remember_key" to str("If the answer might be saved as a preference, what it is, e.g. \"confirmation_method\".")),
            listOf("question")),
        ToolSpec("note", "Save a fact for later turns (event details, options with ISO times, link, your choice).",
            mapOf("text" to str("The fact.")), listOf("text")),
        ToolSpec("check_calendar", "Check the user's calendar for conflicts with dated options.",
            mapOf("options" to mapOf("type" to "array", "items" to mapOf("type" to "object",
                "properties" to mapOf("label" to str("e.g. \"Session A\""), "start" to str("ISO local, e.g. 2026-11-12T10:00"), "end" to str("ISO local")),
                "required" to listOf("label", "start", "end")))),
            listOf("options")),
        ToolSpec("find_photos", "List photos taken on a day, with time and a short description.",
            mapOf("day" to str("\"yesterday\", \"today\" or YYYY-MM-DD.")), listOf("day")),
        ToolSpec("submit", "Press a Submit/Register/Send button after the user approves a spoken read-back.",
            mapOf("node_id" to str("The submit/send button node id."),
                "readback" to str("What will be submitted, spoken: event, time and key fields, or recipient, text and photo."),
                "commit_word" to mapOf("type" to "string", "enum" to listOf("Submit", "Send"))),
            listOf("node_id", "readback", "commit_word")),
        ToolSpec("done", "The goal is finished (or verified as far as possible).",
            mapOf("summary" to str("One spoken sentence.")), listOf("summary")),
        ToolSpec("stop", "Stop because the goal can't be done safely or clearly.",
            mapOf("reason" to str("One spoken sentence.")), listOf("reason")),
    )
}
