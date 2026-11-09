package com.icehacks.saythegoal

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** One-time setup: turn on the accessibility service and grant permissions. Large, readable controls. */
class SetupActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val runtimePermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = "Say the Goal setup"
            textSize = 28f
        })
        status = TextView(this).apply {
            textSize = 20f
            setPadding(0, pad, 0, pad)
        }
        column.addView(status)
        column.addView(bigButton("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        column.addView(bigButton("Grant microphone and other permissions") {
            requestPermissions(runtimePermissions, 1)
        })
        setContentView(ScrollView(this).apply { addView(column, MATCH_PARENT, WRAP_CONTENT) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        fun mark(ok: Boolean) = if (ok) "ON" else "OFF"
        fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            appendLine("Accessibility service: ${mark(serviceEnabled())}")
            appendLine("Microphone: ${mark(granted(Manifest.permission.RECORD_AUDIO))}")
            appendLine("Calendar: ${mark(granted(Manifest.permission.READ_CALENDAR))}")
            appendLine("Photos: ${mark(granted(Manifest.permission.READ_MEDIA_IMAGES))}")
            appendLine("Notifications: ${mark(granted(Manifest.permission.POST_NOTIFICATIONS))}")
            if (BuildConfig.DEBUG) appendLine("\nDebug build: adb hooks active while the service is on.")
        }
    }

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, SayTheGoalAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            .any { ComponentName.unflattenFromString(it) == me }
    }

    private fun bigButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        minHeight = (64 * resources.displayMetrics.density).toInt()
        setOnClickListener { onClick() }
    }
}
