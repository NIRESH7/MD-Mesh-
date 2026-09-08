package com.mdmesh.agent.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.UrlAllowlist

class LockedBrowserActivity : AppCompatActivity() {
    private lateinit var prefs: DevicePrefs
    private lateinit var webView: WebView
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)

        status = TextView(this).apply {
            textSize = 13f
            setPadding(24, 16, 24, 8)
        }
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFF4F7F8.toInt())
                addView(status)
                addView(webView)
                addView(close)
            }
        )

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                return !allowNavigation(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return !allowNavigation(url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (!prefs.blockWebMedia) return null
                if (UrlAllowlist.isBlockedMedia(url, true)) {
                    // Host-based media (YouTube, etc.) — block resource
                    return emptyResponse()
                }
                val lower = url.lowercase()
                if (lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m3u8") ||
                    lower.contains(".mp3") || lower.contains(".m4a")
                ) {
                    return emptyResponse()
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                status.text = url ?: ""
                if (!allowNavigation(url, silent = true)) {
                    view?.stopLoading()
                    Toast.makeText(this@LockedBrowserActivity, "Link not allowed", Toast.LENGTH_SHORT).show()
                    if (view?.canGoBack() == true) view.goBack()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (prefs.blockWebMedia) {
                    view?.evaluateJavascript(MEDIA_KILL_JS, null)
                }
            }
        }

        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "Downloads blocked", Toast.LENGTH_SHORT).show()
        }

        val start = intent.getStringExtra(EXTRA_URL)
            ?: prefs.allowedUrls.firstOrNull()
        if (start.isNullOrBlank()) {
            Toast.makeText(this, "No allowed websites configured", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!allowNavigation(start)) {
            Toast.makeText(this, "Starting URL not on allowlist", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        webView.loadUrl(start)
    }

    private fun allowNavigation(url: String?, silent: Boolean = false): Boolean {
        if (url.isNullOrBlank() || url == "about:blank") return true
        if (url.startsWith("data:", true) || url.startsWith("blob:", true)) return false
        if (prefs.blockWebMedia && UrlAllowlist.isBlockedMedia(url, true)) {
            if (!silent) Toast.makeText(this, "Media blocked", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!prefs.hasWebAllowlist) return true
        val ok = UrlAllowlist.isAllowed(url, prefs.allowedUrls)
        if (!ok && !silent) {
            Toast.makeText(this, "Only admin-allowed links can open", Toast.LENGTH_SHORT).show()
        }
        return ok
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"

        private const val MEDIA_KILL_JS = """
            (function(){
              try {
                document.querySelectorAll('video,audio').forEach(function(el){
                  el.pause();
                  el.removeAttribute('src');
                  el.load();
                  el.style.pointerEvents='none';
                  el.controls=false;
                });
                var s=document.createElement('style');
                s.innerHTML='video,audio{display:none!important}';
                document.documentElement.appendChild(s);
              } catch(e) {}
            })();
        """
    }
}
