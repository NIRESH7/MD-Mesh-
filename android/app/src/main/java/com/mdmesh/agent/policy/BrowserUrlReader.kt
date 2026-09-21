package com.mdmesh.agent.policy

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale
import java.util.regex.Pattern

object BrowserUrlReader {
    private val URL_PATTERN = Pattern.compile(
        "(https?://[^\\s\"'<>]+)|((www\\.)?[a-zA-Z0-9][-a-zA-Z0-9]*\\.[a-zA-Z]{2,}(/[^\\s\"'<>]*)?)",
        Pattern.CASE_INSENSITIVE
    )

    private val URL_BAR_IDS = listOf(
        "com.android.chrome:id/url_bar",
        "com.android.chrome:id/omnibox_url_bar",
        "com.android.chrome:id/url_bar_wrapper",
        "com.android.chrome:id/search_box_text",
        "com.android.chrome:id/title_url",
        "com.chrome.beta:id/url_bar",
        "com.brave.browser:id/url_bar",
        "com.brave.browser:id/omnibox_url_bar",
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx:id/url_bar",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser:id/url_field",
        "com.mi.globalbrowser:id/url",
        "com.android.browser:id/url"
    )

    fun readCurrentUrl(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        for (id in URL_BAR_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()?.trim().orEmpty()
                nodes.forEach { it.recycle() }
                val url = coerceToUrl(text)
                if (url != null) return url
            }
        }
        return findUrlInTree(root, 0)
    }

    private fun findUrlInTree(node: AccessibilityNodeInfo, depth: Int): String? {
        if (depth > 18) return null
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            val url = coerceToUrl(text)
            if (url != null) return url
        }
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty()) {
            val url = coerceToUrl(desc)
            if (url != null) return url
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findUrlInTree(child, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    fun coerceToUrl(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val lower = s.lowercase(Locale.US)
        if (lower.startsWith("chrome://") ||
            lower.startsWith("chrome-native://") ||
            lower.startsWith("about:") ||
            lower.startsWith("brave://") ||
            lower == "new tab" ||
            lower.contains("search or type")
        ) {
            return s
        }
        // Strip scheme-less display like "linkedin.com/feed"
        val m = URL_PATTERN.matcher(s)
        if (m.find()) {
            s = m.group() ?: return null
        } else {
            return null
        }
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "https://$s"
        }
        return s
    }

    fun isBrowserInternal(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase(Locale.US)
        return lower.startsWith("chrome://") ||
            lower.startsWith("chrome-native://") ||
            lower.startsWith("about:") ||
            lower.startsWith("brave://") ||
            lower.startsWith("edge://") ||
            lower == "new tab" ||
            lower.contains("search or type")
    }
}
