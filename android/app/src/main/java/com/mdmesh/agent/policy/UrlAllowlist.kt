package com.mdmesh.agent.policy

import android.net.Uri
import java.util.Locale

/**
 * Matches navigated URLs against admin-configured prefixes
 * (origin + optional path prefix).
 */
object UrlAllowlist {
    private val MEDIA_HOST_HINTS = listOf(
        "youtube.com",
        "youtu.be",
        "googlevideo.com",
        "ytimg.com",
        "vimeo.com",
        "dailymotion.com",
        "twitch.tv",
        "tiktok.com",
        "instagram.com",
        "facebook.com/watch",
        "fb.watch",
        "google.com/imgres",
        "images.google.",
        "imgres",
        "gstatic.com/images"
    )

    private val MEDIA_EXT = listOf(
        ".mp4", ".webm", ".mkv", ".mov", ".m4v", ".avi", ".m3u8",
        ".mp3", ".m4a", ".wav", ".ogg", ".flac"
    )

    fun normalizePrefix(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
            s = "https://$s"
        }
        return try {
            val uri = Uri.parse(s)
            if (uri.scheme != "http" && uri.scheme != "https") return null
            if (uri.host.isNullOrBlank()) return null
            val path = uri.path.orEmpty()
            val base = "${uri.scheme}://${uri.host?.lowercase(Locale.US)}" +
                (if (uri.port != -1) ":${uri.port}" else "")
            if (path.isEmpty() || path == "/") base else base + path.trimEnd('/')
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeList(urls: Collection<String>): List<String> {
        val out = linkedSetOf<String>()
        for (u in urls) {
            normalizePrefix(u)?.let { out += it }
        }
        return out.toList()
    }

    fun isAllowed(url: String?, prefixes: Collection<String>): Boolean {
        if (url.isNullOrBlank()) return false
        if (BrowserUrlReader.isBrowserInternal(url)) return true
        if (prefixes.isEmpty()) return true // no web lockdown configured
        val target = normalizeForCompare(url) ?: return false
        for (raw in prefixes) {
            val prefix = normalizePrefix(raw) ?: continue
            if (target.startsWith(prefix, ignoreCase = true)) return true
            val alt = swapWww(prefix)
            if (alt != null && target.startsWith(alt, ignoreCase = true)) return true
        }
        return false
    }

    fun isBlockedMedia(url: String?, blockMedia: Boolean): Boolean {
        if (!blockMedia || url.isNullOrBlank()) return false
        val lower = url.lowercase(Locale.US)
        if (MEDIA_HOST_HINTS.any { lower.contains(it) }) return true
        val path = try {
            Uri.parse(url).path.orEmpty().lowercase(Locale.US)
        } catch (_: Exception) {
            lower
        }
        return MEDIA_EXT.any { path.endsWith(it) }
    }

    fun isBrowserPackage(pkg: String): Boolean {
        val p = pkg.lowercase(Locale.US)
        return p == "com.android.chrome" ||
            p == "com.chrome.beta" ||
            p == "com.chrome.dev" ||
            p == "com.chrome.canary" ||
            p == "com.brave.browser" ||
            p == "com.brave.browser_beta" ||
            p == "com.microsoft.emmx" ||
            p == "org.mozilla.firefox" ||
            p == "com.opera.browser" ||
            p == "com.sec.android.app.sbrowser" ||
            p == "com.mi.globalbrowser" ||
            p == "com.android.browser" ||
            p.contains("chrome") && p.contains("browser") ||
            p.endsWith(".browser")
    }

    private fun normalizeForCompare(url: String): String? {
        return try {
            val uri = Uri.parse(url.trim())
            if (uri.scheme != "http" && uri.scheme != "https") return null
            val host = uri.host?.lowercase(Locale.US) ?: return null
            val base = "${uri.scheme}://$host" +
                (if (uri.port != -1) ":${uri.port}" else "")
            val path = uri.path.orEmpty().trimEnd('/')
            if (path.isEmpty()) base else base + path
        } catch (_: Exception) {
            null
        }
    }

    private fun swapWww(prefix: String): String? {
        return try {
            val uri = Uri.parse(prefix)
            val host = uri.host ?: return null
            val newHost = when {
                host.startsWith("www.") -> host.removePrefix("www.")
                else -> "www.$host"
            }
            val path = uri.path.orEmpty().trimEnd('/')
            val base = "${uri.scheme}://$newHost" +
                (if (uri.port != -1) ":${uri.port}" else "")
            if (path.isEmpty() || path == "/") base else base + path
        } catch (_: Exception) {
            null
        }
    }
}
