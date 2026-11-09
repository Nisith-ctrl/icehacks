package com.icehacks.saythegoal.device

import com.icehacks.saythegoal.core.Fingerprinter
import com.icehacks.saythegoal.core.ScreenSnapshot
import java.security.MessageDigest

/**
 * Exactly as specified in core: SHA-256 hex of packageName + every node's
 * className|viewId|text|contentDescription|checked|selected, in snapshot order.
 * Nulls are written as an empty string; each node is terminated by '\n'.
 */
class Sha256Fingerprinter : Fingerprinter {
    override fun of(snapshot: ScreenSnapshot): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(snapshot.packageName.toByteArray())
        md.update('\n'.code.toByte())
        for (n in snapshot.nodes) {
            val line = listOf(
                n.className, n.viewId.orEmpty(), n.text.orEmpty(), n.contentDescription.orEmpty(),
                n.checked.toString(), n.selected.toString(),
            ).joinToString("|") + "\n"
            md.update(line.toByteArray())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
