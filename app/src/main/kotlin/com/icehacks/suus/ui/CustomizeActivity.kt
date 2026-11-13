package com.icehacks.suus.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.suus.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * "Make SuuS yours": how SuuS talks (tone), how much it explains, how fast it speaks, how big the
 * robot is, and the app's colours. Every choice is saved and applied the moment it's picked.
 */
class CustomizeActivity : AppCompatActivity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        val (root, column) = page()
        column.addSpaced(bigButton(getString(R.string.back_arrow), ButtonKind.TONAL) { finish() }.apply {
            contentDescription = getString(R.string.back)
        })
        column.addSpaced(heroHeader(getString(R.string.custom_title), getString(R.string.custom_short), getString(R.string.custom_eyebrow)), 16)
        column.addSpaced(bodyText(getString(R.string.custom_intro), 18f), 14)
        val status = bodyText(getString(R.string.custom_saved), 18f, secondary = false).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        column.addSpaced(status, 12)
        customizeSections(column, scope) { status.visibility = View.VISIBLE }
        setContentView(root)
        column.animateLayoutChanges()
        if (savedInstanceState == null) column.revealChildren()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
