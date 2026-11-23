package com.susagi.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.guardian.app.RiskStatus
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.susagi.security.AstraSecurityBridge
import com.susagi.security.UrlSecurityDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * SuSagi Guardian V2 — In-App Safe Browsing Destination.
 *
 * Provides a secure, sandboxed browsing destination when SuSagi holds the default browser role:
 * 1. Sanitizes, validates, and runs offline SafetyBarrier inspection prior to loading.
 * 2. Intercepts all redirects and secondary navigation requests to prevent phishing traps and drive-by APK drops.
 * 3. Enforces strict SSL/TLS integrity (never bypasses certificate errors).
 * 4. Disables dangerous local file and content scheme access.
 * 5. Prevents self-looping intent recursion by explicitly excluding SuSagi when handing off to external browsers.
 */
class SafeBrowsingActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val currentUrlState = mutableStateOf("")
    private val currentTitleState = mutableStateOf("SuSagi Protected Web")
    private val isSecureState = mutableStateOf(true)
    private val loadProgressState = mutableFloatStateOf(0f)
    private val isLoadingState = mutableStateOf(false)
    private val interceptedThreatState = mutableStateOf<UrlSecurityDecision?>(null)
    private val sslErrorDetailsState = mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_URL = "com.susagi.browser.EXTRA_URL"
        private const val TAG = "SafeBrowsingActivity"

        fun createIntent(context: Context, url: String): Intent {
            return Intent(context, SafeBrowsingActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        val initialUrl = if (targetUrl.isBlank()) "https://duckduckgo.com" else targetUrl

        currentUrlState.value = initialUrl
        isSecureState.value = initialUrl.startsWith("https://", ignoreCase = true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    finish()
                }
            }
        })

        setContent {
            GuardianTheme {
                SafeBrowsingScreen(
                    url = currentUrlState.value,
                    title = currentTitleState.value,
                    isSecure = isSecureState.value,
                    progress = loadProgressState.floatValue,
                    isLoading = isLoadingState.value,
                    interceptedThreat = interceptedThreatState.value,
                    sslErrorDetails = sslErrorDetailsState.value,
                    onBackClick = {
                        val wv = webView
                        if (wv != null && wv.canGoBack()) wv.goBack() else finish()
                    },
                    onForwardClick = {
                        webView?.goForward()
                    },
                    onRefreshClick = {
                        webView?.reload()
                    },
                    onCloseClick = {
                        finish()
                    },
                    onCopyUrl = {
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("URL", currentUrlState.value))
                        Toast.makeText(this@SafeBrowsingActivity, "URL copied", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExternal = {
                        openExternalBrowserFiltered(currentUrlState.value)
                    },
                    onDismissThreat = {
                        interceptedThreatState.value = null
                    },
                    onDismissSslError = {
                        sslErrorDetailsState.value = null
                        finish()
                    },
                    onWebViewCreated = { wv ->
                        webView = wv
                        configureAndLoad(wv, initialUrl)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.let {
            it.stopLoading()
            it.clearHistory()
            it.removeAllViews()
            it.destroy()
        }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureAndLoad(wv: WebView, urlToLoad: String) {
        val settings = wv.settings

        // Security Hardening: Disable dangerous file/content scheme access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false

        // Safe web execution
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            settings.safeBrowsingEnabled = true
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadProgressState.floatValue = newProgress / 100f
                isLoadingState.value = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    currentTitleState.value = title
                }
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUri = request?.url ?: return false
                val nextUrl = nextUri.toString()
                val scheme = nextUri.scheme?.lowercase(Locale.ROOT) ?: ""

                // Reject non-web schemes or handle UPI / Tel safely
                if (scheme !in listOf("http", "https")) {
                    if (scheme == "upi") {
                        try {
                            val upiIntent = Intent(Intent.ACTION_VIEW, nextUri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(upiIntent)
                        } catch (_: Exception) {
                            Toast.makeText(this@SafeBrowsingActivity, "No UPI payment app available", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SafeBrowsingActivity, "Blocked unsupported scheme: $scheme", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }

                // Run real-time Astra Security Barrier inspection on redirect/navigation
                lifecycleScope.launch(Dispatchers.Default) {
                    val decision = AstraSecurityBridge.assessUrl(nextUrl)
                    if (decision.isHighRisk) {
                        withContext(Dispatchers.Main) {
                            interceptedThreatState.value = decision
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            currentUrlState.value = nextUrl
                            isSecureState.value = nextUrl.startsWith("https://", ignoreCase = true)
                            view?.loadUrl(nextUrl)
                        }
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                val current = url ?: ""
                currentUrlState.value = current
                isSecureState.value = current.startsWith("https://", ignoreCase = true)
                isLoadingState.value = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                isLoadingState.value = false
                val current = url ?: ""
                currentUrlState.value = current
                isSecureState.value = current.startsWith("https://", ignoreCase = true)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // Strict Security: Never proceed on SSL error
                handler?.cancel()
                val errorDesc = when (error?.primaryError) {
                    SslError.SSL_EXPIRED -> "The security certificate has expired."
                    SslError.SSL_IDMISMATCH -> "Hostname does not match security certificate."
                    SslError.SSL_NOTYETVALID -> "The security certificate is not yet valid."
                    SslError.SSL_UNTRUSTED -> "The certificate authority is untrusted or self-signed."
                    else -> "SSL/TLS verification failed."
                }
                sslErrorDetailsState.value = errorDesc
                Log.w(TAG, "SSL Verification Error: $errorDesc on ${error?.url}")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    Log.w(TAG, "Web navigation error: ${error?.description}")
                }
            }
        }

        // Pre-inspection before initial load
        lifecycleScope.launch(Dispatchers.Default) {
            val decision = AstraSecurityBridge.assessUrl(urlToLoad)
            withContext(Dispatchers.Main) {
                if (decision.isHighRisk) {
                    interceptedThreatState.value = decision
                } else {
                    wv.loadUrl(urlToLoad)
                }
            }
        }
    }

    /**
     * Hand off to an external browser while strictly avoiding the self-opening recursion loop.
     */
    private fun openExternalBrowserFiltered(url: String) {
        val uri = Uri.parse(url)
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolveInfos = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        val externalApp = resolveInfos.firstOrNull {
            val pkg = it.activityInfo.packageName
            pkg != packageName && !pkg.contains("susagi") && !pkg.contains("guardian")
        }

        if (externalApp != null) {
            browserIntent.setClassName(externalApp.activityInfo.packageName, externalApp.activityInfo.name)
            try {
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open external browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No alternate external browser installed. SuSagi Safe Browser is active.", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun SafeBrowsingScreen(
    url: String,
    title: String,
    isSecure: Boolean,
    progress: Float,
    isLoading: Boolean,
    interceptedThreat: UrlSecurityDecision?,
    sslErrorDetails: String?,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCloseClick: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
    onDismissThreat: () -> Unit,
    onDismissSslError: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Security Header & Navigation Bar
            Surface(
                color = GxSurface,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close / Back Button
                        IconButton(onClick = onCloseClick, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = GxTextHi, modifier = Modifier.size(20.dp))
                        }

                        // URL Display Box
                        Surface(
                            shape = GxShapeSm,
                            color = GxSurfaceAlt,
                            border = BorderStroke(1.dp, if (isSecure) GxSafe.copy(alpha = 0.5f) else GxWarning),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSecure) Icons.Default.Lock else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = if (isSecure) GxSafe else GxWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title.take(28),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GxTextHi,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = url.take(45),
                                        fontSize = 9.sp,
                                        color = GxTextLo,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // Quick Actions: Copy, Refresh, Open in Browser
                        IconButton(onClick = onCopyUrl, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = GxTextMid, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onRefreshClick, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = GxTextMid, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onOpenExternal, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = "External", tint = GxPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Progress Bar
                    if (isLoading && progress < 1f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = GxPrimary,
                            trackColor = GxSurfaceAlt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    }

                    // SuSagi Security Badge Strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GxSurfaceAlt)
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = GxSafe, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SuSagi Zero-Trust Safe Browsing", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GxSafe)
                        }
                        Text("SANDBOXED OFFLINE SHIELD", fontSize = 8.sp, color = GxTextLo, letterSpacing = 0.5.sp)
                    }
                }
            }

            // Android WebView Container
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            onWebViewCreated(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Threat Interception Modal Overlay
        interceptedThreat?.let { threat ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(2.dp, GxDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GxDanger, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "MALICIOUS NAVIGATION BLOCKED",
                                color = GxDanger,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = "Astra SafetyBarrier intercepted an attempted redirect or navigation to a dangerous destination.",
                            color = GxTextHi,
                            fontSize = 12.sp
                        )

                        Surface(
                            color = GxDangerSoft,
                            shape = GxShapeSm,
                            border = BorderStroke(0.5.dp, GxDanger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(threat.title, fontWeight = FontWeight.Bold, color = GxDanger, fontSize = 12.sp)
                                Text(threat.detail, color = GxTextMid, fontSize = 11.sp)
                                if (threat.reasons.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    threat.reasons.forEach { r ->
                                        Text("• $r", color = GxTextHi, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onDismissThreat,
                            colors = ButtonDefaults.buttonColors(containerColor = GxDanger, contentColor = Color.White),
                            shape = GxShapeSm,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stay Safe & Return", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // SSL Certificate Error Modal
        sslErrorDetails?.let { sslError ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(2.dp, GxDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GxDanger, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SSL / TLS SECURITY FAILURE", color = GxDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Text(
                            text = "SuSagi Guardian has aborted navigation because the server's identity could not be verified securely:\n\n$sslError\n\nTo protect you from credential theft and man-in-the-middle attacks, this page will not be loaded.",
                            color = GxTextHi,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = onDismissSslError,
                            colors = ButtonDefaults.buttonColors(containerColor = GxDanger, contentColor = Color.White),
                            shape = GxShapeSm,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close Navigation", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
