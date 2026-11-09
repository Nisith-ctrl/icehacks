package com.icehacks.saythegoal.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.icehacks.saythegoal.core.Bounds
import com.icehacks.saythegoal.core.ScreenSnapshot
import com.icehacks.saythegoal.core.ScrollDirection
import com.icehacks.saythegoal.core.UiNode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads the active app window from the accessibility tree and performs raw actions.
 * Only TYPE_APPLICATION windows are read, so our overlay (TYPE_ACCESSIBILITY_OVERLAY), the IME
 * and system UI never appear in snapshots or fingerprints.
 */
class AccessibilityBackend(
    private val service: AccessibilityService,
    private val maxNodes: Int = 2_000,
) : ScreenBackend<AccessibilityNodeInfo> {

    override suspend fun capture(): Captured<AccessibilityNodeInfo> {
        val window = activeAppWindow()
        val root = window?.root ?: service.rootInActiveWindow
            ?: return Captured(ScreenSnapshot("", null, System.currentTimeMillis(), emptyList()), emptyList())

        val nodes = ArrayList<UiNode>()
        val handles = ArrayList<AccessibilityNodeInfo>()
        val ids = HashMap<AccessibilityNodeInfo, String>()
        val labeledBy = HashMap<Int, AccessibilityNodeInfo>()

        fun visit(info: AccessibilityNodeInfo, parentId: String?, isRoot: Boolean) {
            if (nodes.size >= maxNodes) return
            if (!isRoot && !info.isVisibleToUser) return
            val id = "n${nodes.size}"
            ids[info] = id
            info.labeledBy?.let { labeledBy[nodes.size] = it }
            val r = Rect().also { info.getBoundsInScreen(it) }
            nodes += UiNode(
                id = id,
                parentId = parentId,
                className = info.className?.toString() ?: "android.view.View",
                text = info.text?.toString(),
                contentDescription = info.contentDescription?.toString(),
                hint = info.hintText?.toString(),
                viewId = info.viewIdResourceName,
                labeledById = null,
                bounds = Bounds(r.left, r.top, r.right, r.bottom),
                clickable = info.isClickable,
                editable = info.isEditable,
                scrollable = info.isScrollable,
                checkable = info.isCheckable,
                checked = info.isChecked,
                selected = info.isSelected,
                focused = info.isFocused,
                enabled = info.isEnabled,
            )
            handles += info
            for (c in 0 until info.childCount) {
                info.getChild(c)?.let { visit(it, id, false) }
            }
        }
        visit(root, null, true)

        for ((index, label) in labeledBy) {
            ids[label]?.let { nodes[index] = nodes[index].copy(labeledById = it) }
        }

        val pkg = root.packageName?.toString() ?: ""
        val title = window?.title?.toString()
        return Captured(ScreenSnapshot(pkg, title, System.currentTimeMillis(), nodes), handles)
    }

    /** The focused/active application window; falls back to the top-most application window. */
    private fun activeAppWindow(): AccessibilityWindowInfo? {
        val apps = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return apps.firstOrNull { it.isActive } ?: apps.firstOrNull { it.isFocused } ?: apps.maxByOrNull { it.layer }
    }

    override suspend fun click(handle: AccessibilityNodeInfo, bounds: Bounds): Boolean {
        var n: AccessibilityNodeInfo? = handle
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tapAt((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f)
    }

    override suspend fun setText(handle: AccessibilityNodeInfo, text: String): Boolean {
        handle.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return handle.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun scroll(handle: AccessibilityNodeInfo?, direction: ScrollDirection): Boolean {
        var n: AccessibilityNodeInfo? = handle ?: largestScrollable()
        while (n != null && !n.isScrollable) n = n.parent
        val action = if (direction == ScrollDirection.DOWN) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (n != null && n.performAction(action)) return true

        // Fallback: swipe inside the target (or the whole screen).
        val r = Rect()
        (n ?: handle ?: activeAppWindow()?.root)?.getBoundsInScreen(r)
            ?: service.resources.displayMetrics.let { r.set(0, 0, it.widthPixels, it.heightPixels) }
        val x = r.exactCenterX()
        val (y1, y2) = r.top + r.height() * 0.75f to r.top + r.height() * 0.25f
        return if (direction == ScrollDirection.DOWN) swipe(x, y1, x, y2) else swipe(x, y2, x, y1)
    }

    private fun largestScrollable(): AccessibilityNodeInfo? {
        val root = activeAppWindow()?.root ?: service.rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val r = Rect()
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n.isScrollable && n.isVisibleToUser) {
                n.getBoundsInScreen(r)
                val area = r.width() * r.height()
                if (area > bestArea) { best = n; bestArea = area }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { stack.add(it) }
        }
        return best
    }

    override suspend fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    override suspend fun launch(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { service.startActivity(intent) }.isSuccess
    }

    private suspend fun tapAt(x: Float, y: Float): Boolean =
        gesture(Path().apply { moveTo(x, y) }, durationMs = 50)

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, durationMs = 300)

    private suspend fun gesture(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { cont ->
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val cb = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }
            if (!service.dispatchGesture(g, cb, null) && cont.isActive) cont.resume(false)
        }
}
