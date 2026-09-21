package com.mdmesh.agent.policy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Recents overlay helpers: lock Pandiyan so Clear All can close other apps,
 * and detect when the user tries to close Pandiyan itself.
 */
object RecentsCardGuard {
    fun isRecentsPackage(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "com.android.systemui" ||
            p.contains("systemui") ||
            p == "com.miui.home" ||
            p.contains("miui.home") ||
            p.contains("recents") ||
            p.contains("android.launcher3") ||
            p.contains("nexuslauncher") ||
            p.contains("overview")
    }

    /** SystemUI / MIUI Home when used as Recents host (not normal Home icons). */
    fun looksLikeRecentsPackageOnly(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "com.android.systemui" ||
            p.contains("systemui") ||
            p.contains("recents") ||
            p.contains("overview")
    }

    fun looksLikeRecentsClass(className: String?): Boolean {
        val c = className.orEmpty().lowercase()
        return c.contains("recents") ||
            c.contains("overview") ||
            c.contains("taskstack") ||
            c.contains("recentapps") ||
            c.contains("relativetask") ||
            c.contains("activitymanagerview")
    }

    fun looksLikeRecentsUi(root: AccessibilityNodeInfo): Boolean {
        return hasRecentsMarker(root)
    }

    fun isClearAllClick(event: AccessibilityEvent): Boolean {
        return looksLikeClearAll(nodeBits(event)) || isMiuiClearAllControl(event)
    }

    /** MIUI Clear All is often a center bottom X / clearAnim with little text. */
    fun isMiuiClearAllControl(event: AccessibilityEvent): Boolean {
        val id = runCatching { event.source?.viewIdResourceName?.lowercase().orEmpty() }.getOrDefault("")
        if (id.contains("clear") || id.contains("clear_anim") || id.contains("clearanim") ||
            id.contains("recent_clear") || id.contains("btn_clear") || id.contains("memory_clear")
        ) {
            return true
        }
        val bits = nodeBits(event)
        if (looksLikeClearAll(bits)) return true
        // Bare "X" / "×" while in Recents is usually Clear All on MIUI
        val t = bits.trim()
        return t == "x" || t == "×" || t == "✕" || t.contains("clearanim")
    }

    private fun hasRecentsMarker(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val bits = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (id.contains("recent") || id.contains("overview") || id.contains("task_stack") ||
            id.contains("recents") || id.contains("clear_anim") || id.contains("clearanim") ||
            id.contains("memory_clear") || id.contains("btn_clear")
        ) {
            return true
        }
        if (looksLikeClearAll(bits) || bits.contains("clear all") || bits.contains("close all") ||
            bits.contains("cleaner")
        ) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasRecentsMarker(child)) return true
        }
        return false
    }

    fun isOurCardCloseClick(event: AccessibilityEvent, root: AccessibilityNodeInfo?): Boolean {
        val bits = nodeBits(event)
        if (!looksLikeClose(bits)) return false
        if (rootHasOurLabel(root) || bits.contains("pandiyan")) return true
        // MIUI close X on the focused card while Recents shows our title
        return rootHasOurLabel(root)
    }

    fun looksLikeClearAll(bits: String): Boolean {
        val t = bits.lowercase()
        return t.contains("clear all") ||
            t.contains("close all") ||
            t.contains("remove all") ||
            t.contains("全部清除") ||
            t.contains("一键清理") ||
            t.contains("全部关闭") ||
            (t.contains("clear") && t.contains("all")) ||
            t.contains("clearanim")
    }

    fun autoLockOurCard(service: AccessibilityService, root: AccessibilityNodeInfo): Boolean {
        val our = findNodeWithOurLabel(root) ?: return false
        val lock = findLockControl(root) ?: findLockNear(our)
        if (lock != null && click(lock)) return true
        // MIUI: pull down on the card to pin/lock it
        val card = cardBounds(our)
        if (card.height() > 80 && card.width() > 80) {
            swipeDown(service, card)
            return true
        }
        return false
    }

    private fun nodeBits(event: AccessibilityEvent): String = buildString {
        event.text?.forEach { append(it).append(' ') }
        append(event.contentDescription ?: "")
        runCatching {
            event.source?.let { src ->
                append(src.text ?: "")
                append(' ')
                append(src.contentDescription ?: "")
                append(' ')
                append(src.viewIdResourceName ?: "")
            }
        }
    }.lowercase()

    private fun looksLikeClose(t: String): Boolean {
        val s = t.lowercase().trim()
        if (looksLikeClearAll(s)) return false
        return s == "x" ||
            s == "×" ||
            s.contains("close") ||
            s.contains("dismiss") ||
            s.contains("remove") ||
            s.contains("关闭") ||
            (s.contains("clear") && !s.contains("all"))
    }

    private fun rootHasOurLabel(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return findNodeWithOurLabel(root) != null
    }

    private fun findNodeWithOurLabel(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val bits = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (bits.contains("pandiyan")) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeWithOurLabel(child)
            if (found != null) return found
        }
        return null
    }

    private fun findLockControl(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        val bits = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        val isUnlock = bits.contains("unlock") || bits.contains("解锁") || bits.contains("unlocked")
        val isLock = !isUnlock && (
            bits.contains("lock") || bits.contains("锁定") || bits.contains("padlock") ||
                id.contains("lock")
            )
        if (isLock && (node.isClickable || node.parent?.isClickable == true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findLockControl(child)
            if (found != null) return found
        }
        return null
    }

    private fun findLockNear(our: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = our
        repeat(6) {
            p = p?.parent ?: return null
            val found = findLockControl(p!!)
            if (found != null) return found
        }
        return null
    }

    private fun cardBounds(node: AccessibilityNodeInfo): Rect {
        var cur: AccessibilityNodeInfo? = node
        var best = Rect()
        node.getBoundsInScreen(best)
        repeat(8) {
            val p = cur?.parent ?: return best
            val r = Rect()
            p.getBoundsInScreen(r)
            if (r.height() > best.height() && r.height() < 1600) best = r
            cur = p
        }
        return best
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var p = node.parent
        var depth = 0
        while (p != null && depth < 5) {
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            p = p.parent
            depth++
        }
        return false
    }

    private fun swipeDown(service: AccessibilityService, card: Rect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cx = card.exactCenterX()
        val y1 = card.top + 24f
        val y2 = (card.top + card.height().coerceAtMost(280) * 0.35f)
        val path = Path().apply {
            moveTo(cx, y1)
            lineTo(cx, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 180)
        runCatching {
            service.dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                null,
                null
            )
        }
    }
}
