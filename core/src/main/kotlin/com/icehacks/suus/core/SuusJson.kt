package com.icehacks.suus.core

import kotlinx.serialization.json.Json

/**
 * The one Json config for snapshots, actions and results (fixtures, debug broadcasts, tests).
 * Sealed types use a "type" discriminator with short names, e.g.
 * {"type":"Tap","nodeId":"n12"} or {"type":"Back"}.
 */
val SuusJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}
