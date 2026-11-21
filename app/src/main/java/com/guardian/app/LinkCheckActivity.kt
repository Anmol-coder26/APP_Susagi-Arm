package com.guardian.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.guardian.app.protect.RegionalScamKeywords
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import java.util.Locale

data class LinkVerdict(
    val url: String,
    val status: RiskStatus,
    val title: String,
    val detail: String,
    val riskScore: Int = when (status) {
        RiskStatus.High -> 85
        RiskStatus.Suspicious -> 50
        RiskStatus.Low -> 10
    }
)

sealed class SharedPayload {
    object Empty : SharedPayload()
    data class ViewUrl(val url: String) : SharedPayload()
    data class SharedContent(
        val rawText: String,
        val urls: List<String>
    ) : SharedPayload()
}

data class UnifiedSecurityAssessment(
    val actualRiskScore: Int,
    val status: RiskStatus,
    val categoryTitle: String,
    val plainExplanationEn: String,
    val plainExplanationHi: String,
    val detectedSignals: List<String>,
    val evaluatedUrls: List<LinkVerdict>,
    val engineLabel: String = "SuSagi Offline Heuristic Rule Engine (Astra SafetyBarrier Fallback)",
    val isTextOnly: Boolean = false,
    val rawTextSnippet: String = ""
)

class LinkCheckActivity : ComponentActivity() {

    private val assessmentState = mutableStateOf<UnifiedSecurityAssessment?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(incomingIntent: Intent?) {
        // Anti-recursion check: prevent recursive intent bouncing if SuSagi opened an external browser
        if (incomingIntent?.getBooleanExtra(EXTRA_PROCESSED_BY_GUARDIAN, false) == true) {
            finish()
            return
        }

        val payload = extractSharedPayload(incomingIntent)

        // Privacy-first logging: log metadata only, never raw PII or message body
        when (payload) {
            is SharedPayload.Empty -> Log.d(TAG, "Incoming intent: Empty or unsupported payload")
            is SharedPayload.ViewUrl -> Log.d(TAG, "Incoming intent: ACTION_VIEW URL (length: ${payload.url.length})")
            is SharedPayload.SharedContent -> Log.d(TAG, "Incoming intent: ACTION_SEND text (chars: ${payload.rawText.length}, urls: ${payload.urls.size})")
        }

        val assessment = assessPayload(payload)
        assessmentState.value = assessment

        setContent {
            GuardianTheme {
                val currentAssessment = assessmentState.value ?: assessment
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    UnifiedSecurityDialog(
                        assessment = currentAssessment,
                        onProceedToUrl = { urlToOpen ->
                            openInExternalBrowser(urlToOpen)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        },
                        onCopyReport = {
                            copyReportToClipboard(currentAssessment)
                        },
                        onAlertFamily = {
                            shareFamilyAlert(currentAssessment)
                        }
                    )
                }
            }
        }
    }

    private fun copyReportToClipboard(assessment: UnifiedSecurityAssessment) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val report = buildString {
            appendLine("🛡️ SuSagi Guardian Security Report")
            appendLine("Verdict: ${assessment.categoryTitle} (${assessment.actualRiskScore}% Risk)")
            appendLine("Engine: ${assessment.engineLabel}")
            appendLine("Guidance: ${assessment.plainExplanationEn}")
            if (assessment.detectedSignals.isNotEmpty()) {
                appendLine("Signals: ${assessment.detectedSignals.joinToString(", ")}")
            }
            if (assessment.evaluatedUrls.isNotEmpty()) {
                appendLine("Links Inspected: ${assessment.evaluatedUrls.size}")
                assessment.evaluatedUrls.forEach { v ->
                    appendLine("- ${v.url} [${v.status}]")
                }
            }
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("SuSagi Security Report", report))
        Toast.makeText(this, "Security assessment copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareFamilyAlert(assessment: UnifiedSecurityAssessment) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "⚠️ SuSagi Alert: ${assessment.categoryTitle}")
            putExtra(
                Intent.EXTRA_TEXT,
                "⚠️ SuSagi Guardian Alert:\nThreat: ${assessment.categoryTitle} (${assessment.actualRiskScore}% Risk)\n\n" +
                        "Advice: ${assessment.plainExplanationEn}\n\n" +
                        "हिंदी: ${assessment.plainExplanationHi}\n\n" +
                        "Stay safe! Do not send money, share OTPs, or click unverified links."
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Alert Family Member via"))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open share chooser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInExternalBrowser(url: String) {
        val uri = try {
            val parsed = Uri.parse(url)
            if (parsed.scheme == null) Uri.parse("https://$url") else parsed
        } catch (_: Exception) {
            Uri.parse(url)
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            putExtra(EXTRA_PROCESSED_BY_GUARDIAN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val resolveInfos = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        val targetApp = resolveInfos.firstOrNull {
            val pkg = it.activityInfo.packageName
            pkg != packageName && !pkg.contains("guardian") && !pkg.contains("susagi")
        }

        if (targetApp != null) {
            browserIntent.setClassName(targetApp.activityInfo.packageName, targetApp.activityInfo.name)
            try {
                startActivity(browserIntent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Cannot launch targeted browser: ${e.message}")
            }
        }

        // Fallback browser chooser
        try {
            val chooser = Intent.createChooser(browserIntent, "Open with external browser").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch browser chooser: ${e.message}")
        }
    }

    companion object {
        const val EXTRA_PROCESSED_BY_GUARDIAN = "com.guardian.app.PROCESSED_BY_GUARDIAN"
        private const val TAG = "SuSagiShareShield"

        val URL_REGEX = Regex(
            "(?:https?://|www\\.)[a-zA-Z0-9\\-\\._~:/?#\\[\\]@!$&'()*+,;=%]+",
            RegexOption.IGNORE_CASE
        )

        private val IP_REGEX = Regex(
            "^(?:\\d{1,3}\\.){3}\\d{1,3}$"
        )

        fun extractUrlFromIntent(intent: Intent?): String {
            if (intent == null) return ""
            val payload = extractSharedPayload(intent)
            return when (payload) {
                is SharedPayload.ViewUrl -> payload.url
                is SharedPayload.SharedContent -> payload.urls.firstOrNull().orEmpty()
                is SharedPayload.Empty -> ""
            }
        }

        fun cleanTrailingPunctuation(rawUrl: String): String {
            var url = rawUrl.trim()
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith("!") || url.endsWith("?") || url.endsWith(";") || url.endsWith(")")) {
                url = url.substring(0, url.length - 1).trim()
            }
            return url
        }

        fun extractAllUrls(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            return URL_REGEX.findAll(text).map { match ->
                var url = cleanTrailingPunctuation(match.value)
                if (url.startsWith("www.", ignoreCase = true)) {
                    url = "https://$url"
                }
                url
            }.filter { it.isNotBlank() }.distinct().toList()
        }

        fun extractUrlFromText(text: String): String {
            return extractAllUrls(text).firstOrNull().orEmpty()
        }

        fun extractSharedPayload(intent: Intent?): SharedPayload {
            if (intent == null) return SharedPayload.Empty

            if (intent.action == Intent.ACTION_VIEW) {
                val dataStr = intent.dataString?.trim().orEmpty()
                if (dataStr.isNotBlank()) {
                    return SharedPayload.ViewUrl(url = dataStr)
                }
            }

            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val clipText = if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                intent.clipData!!.getItemAt(0).text?.toString()?.trim().orEmpty()
            } else ""
            val clipUri = if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                intent.clipData!!.getItemAt(0).uri?.toString()?.trim().orEmpty()
            } else ""
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()

            val combinedText = when {
                extraText.isNotBlank() && subject.isNotBlank() && !extraText.contains(subject) -> "$subject\n$extraText"
                extraText.isNotBlank() -> extraText
                clipText.isNotBlank() -> clipText
                clipUri.isNotBlank() -> clipUri
                else -> ""
            }

            if (combinedText.isBlank()) {
                return SharedPayload.Empty
            }

            val urls = extractAllUrls(combinedText)
            return SharedPayload.SharedContent(
                rawText = combinedText,
                urls = urls
            )
        }

        fun extractHost(url: String): String {
            val clean = url.trim().lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
            val afterUser = if (clean.contains("@")) clean.substringAfter("@") else clean
            return afterUser.split('/', ':', '?', '#').firstOrNull()?.trim() ?: ""
        }

        fun extractUserInfo(url: String): String? {
            val clean = url.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("HTTP://")
                .removePrefix("HTTPS://")
            val beforeSlash = clean.split('/', '?', '#').firstOrNull() ?: ""
            return if (beforeSlash.contains("@")) {
                beforeSlash.substringBefore("@")
            } else {
                null
            }
        }

        fun isTrustedHost(host: String, trustedRoots: List<String>): Boolean {
            val cleanHost = host.lowercase(Locale.ROOT).trim().removeSuffix(".")
            if (cleanHost.isBlank()) return false
            return trustedRoots.any { root ->
                val cleanRoot = root.lowercase(Locale.ROOT).trim().removeSuffix(".")
                cleanHost == cleanRoot || cleanHost.endsWith(".$cleanRoot")
            }
        }

        fun hasMixedScriptHomoglyphs(label: String): Boolean {
            var hasLatin = false
            var hasNonLatinConfusable = false
            for (ch in label) {
                if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '-') {
                    hasLatin = true
                } else {
                    val script = Character.UnicodeScript.of(ch.code)
                    if (script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.GREEK) {
                        hasNonLatinConfusable = true
                    }
                }
            }
            return hasLatin && hasNonLatinConfusable
        }

        fun normalizeHomoglyphs(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    '\u0430', '\u0410' -> sb.append('a') // Cyrillic a
                    '\u0435', '\u0415' -> sb.append('e') // Cyrillic e
                    '\u043E', '\u041E' -> sb.append('o') // Cyrillic o
                    '\u0440', '\u0420' -> sb.append('p') // Cyrillic p
                    '\u0441', '\u0421' -> sb.append('c') // Cyrillic c
                    '\u0443', '\u0423' -> sb.append('y') // Cyrillic y
                    '\u0445', '\u0425' -> sb.append('x') // Cyrillic x
                    '\u0456', '\u0406' -> sb.append('i') // Cyrillic i
                    '\u03BF', '\u039F' -> sb.append('o') // Greek omicron
                    '\u03B1', '\u0391' -> sb.append('a') // Greek alpha
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun evaluateLinkSafety(url: String): LinkVerdict {
            val trimmed = url.trim()
            val lower = trimmed.lowercase(Locale.ROOT)

            val host = extractHost(trimmed)
            val userInfo = extractUserInfo(trimmed)
            val path = try {
                val afterScheme = lower.removePrefix("http://").removePrefix("https://")
                val slashIdx = afterScheme.indexOf('/')
                if (slashIdx != -1) afterScheme.substring(slashIdx).split('?', '#').firstOrNull() ?: "" else ""
            } catch (_: Exception) { "" }

            // 0. Threat: Malformed or Empty Hostname
            if (host.isBlank() || host.startsWith(".") || host.endsWith(".") || host.contains("..") ||
                (!host.contains(".") && host != "localhost")) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ MALFORMED OR EMPTY HOSTNAME",
                    detail = "The web address '$url' does not specify a valid, well-formed host domain.",
                    riskScore = 55
                )
            }

            // 1. Critical Threat: Direct IP Hostname (classic malware C2 & phishing tactic)
            if (host.isNotBlank() && IP_REGEX.matches(host)) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 UNVERIFIED IP HOST DETECTED",
                    detail = "This link points directly to a raw IP address ($host) rather than a registered domain name. Legitimate institutions never use raw IP URLs.",
                    riskScore = 88
                )
            }

            // 2. Critical Threat: UserInfo URL Deception (e.g. https://sbi.co.in@evil-server.com/login)
            if (!userInfo.isNullOrBlank()) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 URL IMPERSONATION SPOOF DETECTED",
                    detail = "This link uses user-info prefixing ($userInfo) to disguise the true destination domain ($host).",
                    riskScore = 90
                )
            }

            // 3. Critical Threat: Direct APK or Executable Sideloading
            if (lower.endsWith(".apk") || lower.contains(".apk?") || path.endsWith(".apk") ||
                lower.endsWith(".exe") || lower.endsWith(".dex") || lower.endsWith(".vbs") || lower.endsWith(".sh")) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 MALICIOUS PACKAGE / APK SIDELOAD DETECTED",
                    detail = "This link initiates an unauthorized APK or binary download outside of Google Play, frequently used in RAT (Remote Access Trojan) fraud.",
                    riskScore = 92
                )
            }

            // 4. Critical Threat: Phishing Keywords in Path or Host
            val highRiskKeywords = listOf(
                "login-kyc", "rbi-claim", "rbi-alert", "update-pan", "bank-verify", "digital-arrest",
                "police-notice", "cbi-warrant", "customs-duty", "electricity-bill-pay", "aadhaar-link",
                "anydesk", "teamviewer", "quicksupport", "free-gift", "fastag-fine", "chalan-pay",
                "epfo-claim", "telegram-task", "sbi-kyc", "icici-kyc", "hdfc-update", "paytm-kyc"
            )
            for (keyword in highRiskKeywords) {
                if (lower.contains(keyword)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 DANGEROUS PHISHING / EXTORTION LINK",
                        detail = "This destination has been flagged for active banking credential harvesting, digital arrest extortion, or fraudulent payment gateway impersonation.",
                        riskScore = 86
                    )
                }
            }

            // 5. Critical Threat: Subdomain Impersonation of Financial/Gov Institutions
            val brandPrefixes = listOf("sbi", "hdfc", "icici", "pnb", "axis", "rbi", "incometax", "epfo", "cyberpolice", "uidai")
            val trustedBankAndGovRoots = listOf(
                "sbi.co.in", "onlinesbi.sbi", "rbi.org.in", "hdfcbank.com", "icicibank.com",
                "axisbank.com", "pnbindia.in", "bankofbaroda.in", "kotak.com", "gov.in", "nic.in"
            )

            val decodedHost = try {
                java.net.IDN.toUnicode(host)
            } catch (_: Exception) { host }
            val normalizedDecodedHost = normalizeHomoglyphs(decodedHost.lowercase(Locale.ROOT))

            for (brand in brandPrefixes) {
                val matchesHost = host.contains(brand) || normalizedDecodedHost.contains(brand)
                if (matchesHost && !isTrustedHost(host, trustedBankAndGovRoots)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 FAKE BRAND IMPERSONATION DETECTED",
                        detail = "The domain '$host' mimics official financial or government infrastructure ($brand) on an unauthorized server.",
                        riskScore = 84
                    )
                }
            }

            // 6. Threat Analysis: IDN Homoglyph / Punycode
            val isPunycode = host.startsWith("xn--") || host.contains(".xn--")
            if (isPunycode) {
                val labels = decodedHost.split('.')
                val hasHomoglyphSpoof = labels.any { hasMixedScriptHomoglyphs(it) }
                if (hasHomoglyphSpoof) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 PUNYCODE / HOMOGLYPH SPOOF DETECTED",
                        detail = "This link uses mixed-script internationalized characters ('$decodedHost') that look identical to genuine Latin letters to deceive users.",
                        riskScore = 89
                    )
                }

                val isNationalIdn = host.endsWith(".xn--h2brj9c") || host.endsWith(".xn--mgbbh1a71e") ||
                        host.endsWith(".xn--fpcrj9c3d") || host.endsWith(".xn--gecrj9c") ||
                        host.endsWith(".xn--s9brj9c") || host.endsWith(".xn--xkc2dl3a5ee0h") ||
                        host.endsWith(".xn--45brj9c") || host.endsWith(".xn--2scrj9c") ||
                        host.endsWith(".xn--rvcs84f") || host.endsWith(".xn--3hcrj9c")

                if (!isNationalIdn && (host.endsWith(".com") || host.endsWith(".net") || host.endsWith(".org") ||
                            host.endsWith(".xyz") || host.endsWith(".top") || host.endsWith(".info"))) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 PUNYCODE DOMAIN DETECTED",
                        detail = "This link uses internationalized encoding ('$decodedHost') on an unverified commercial registry.",
                        riskScore = 78
                    )
                }
            }

            // 7. Suspicious Threat: Masked Shorteners
            val shorteners = listOf(
                "bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly", "rb.gy", "ow.ly", "buff.ly", "shorte.st", "adf.ly"
            )
            if (shorteners.any { host.contains(it) || lower.contains(it) }) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ SUSPICIOUS MASKED URL SHORTENER",
                    detail = "This link uses a URL shortener that conceals the true landing page. Proceed with caution.",
                    riskScore = 52
                )
            }

            // 8. Suspicious Threat: High-Abuse TLDs
            val suspiciousTlds = listOf(
                ".xyz", ".top", ".cc", ".club", ".tk", ".ml", ".ga", ".cf", ".gq",
                ".buzz", ".rest", ".live", ".work", ".icu", ".quest", ".monster",
                ".click", ".stream", ".download", ".racing"
            )
            if (suspiciousTlds.any { host.endsWith(it) }) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ UNVERIFIED HIGH-RISK DOMAIN REGISTRY",
                    detail = "This website is hosted on a top-level domain frequently associated with throwaway phishing sites and malicious campaigns.",
                    riskScore = 58
                )
            }

            // 9. Clean Domain
            return LinkVerdict(
                url = url,
                status = RiskStatus.Low,
                title = "🛡️ NO KNOWN THREAT DETECTED",
                detail = "This destination matches known legitimate domain standards with no active threat indicators.",
                riskScore = 8
            )
        }

        fun assessPayload(payload: SharedPayload): UnifiedSecurityAssessment {
            return when (payload) {
                is SharedPayload.Empty -> {
                    UnifiedSecurityAssessment(
                        actualRiskScore = 0,
                        status = RiskStatus.Low,
                        categoryTitle = "NO ANALYZABLE CONTENT",
                        plainExplanationEn = "The shared content did not contain readable message text or web links.",
                        plainExplanationHi = "साझा की गई सामग्री में कोई पठनीय संदेश या वेब लिंक नहीं मिला।",
                        detectedSignals = emptyList(),
                        evaluatedUrls = emptyList(),
                        isTextOnly = true
                    )
                }
                is SharedPayload.ViewUrl -> {
                    val verdict = evaluateLinkSafety(payload.url)
                    val signals = mutableListOf<String>()
                    if (verdict.title.contains("IP HOST", ignoreCase = true)) signals.add("Direct Numeric IP Host")
                    if (verdict.title.contains("HOMOGLYPH", ignoreCase = true) || verdict.title.contains("PUNYCODE", ignoreCase = true)) signals.add("Homoglyph / Punycode Spoof")
                    if (verdict.title.contains("IMPERSONATION", ignoreCase = true)) signals.add("Brand / Institutional Impersonation")
                    if (verdict.title.contains("APK", ignoreCase = true) || verdict.title.contains("PACKAGE", ignoreCase = true)) signals.add("Direct Executable / APK Sideload")
                    if (verdict.title.contains("SHORTENER", ignoreCase = true)) signals.add("Masked Shortener Destination")
                    if (signals.isEmpty() && verdict.status != RiskStatus.Low) signals.add(verdict.title)

                    val explanationEn = when (verdict.status) {
                        RiskStatus.High -> "This web address exhibits characteristics of malicious infrastructure designed to compromise your device or harvest credentials."
                        RiskStatus.Suspicious -> "This link uses unverified domains or masking techniques. Proceed with caution."
                        RiskStatus.Low -> "No known malicious patterns or homoglyph spoofs were detected for this destination."
                    }
                    val explanationHi = when (verdict.status) {
                        RiskStatus.High -> "यह वेब पता दुर्भावनापूर्ण सर्वर से जुड़ा है जो आपकी सुरक्षा या बैंक विवरण को खतरे में डाल सकता है।"
                        RiskStatus.Suspicious -> "इस लिंक में अपुष्ट डोमेन या शॉर्टनर का उपयोग किया गया है। सावधानी बरतें।"
                        RiskStatus.Low -> "इस गंतव्य के लिए कोई ज्ञात सुरक्षा खतरा या दुर्भावनापूर्ण पैटर्न नहीं मिला।"
                    }

                    UnifiedSecurityAssessment(
                        actualRiskScore = verdict.riskScore,
                        status = verdict.status,
                        categoryTitle = if (verdict.status == RiskStatus.Low) "UNFLAGGED DESTINATION" else verdict.title,
                        plainExplanationEn = explanationEn,
                        plainExplanationHi = explanationHi,
                        detectedSignals = signals,
                        evaluatedUrls = listOf(verdict),
                        isTextOnly = false,
                        rawTextSnippet = payload.url
                    )
                }
                is SharedPayload.SharedContent -> {
                    val text = payload.rawText
                    val evaluatedUrls = payload.urls.map { evaluateLinkSafety(it) }

                    // Real offline heuristic rule scoring
                    val keywordReport = KeywordScorer.score(text)
                    var highestRegionalScore = 0
                    val regionalMatches = mutableListOf<String>()
                    val supportedLangs = listOf("hi", "en", "ta", "te", "bn", "mr", "kn", "ml", "pa", "gu")
                    for (lang in supportedLangs) {
                        val sc = RegionalScamKeywords.score(text, lang)
                        if (sc > highestRegionalScore) highestRegionalScore = sc
                        val m = RegionalScamKeywords.match(text, lang)
                        m.forEach { regionalMatches.add(it.phrase) }
                    }

                    val maxUrlScore = evaluatedUrls.maxOfOrNull { it.riskScore } ?: 0
                    val textScore = maxOf(keywordReport.riskScore, highestRegionalScore)
                    val combinedScore = if (textScore >= 35 && maxUrlScore >= 50) {
                        // Compound threat: deceptive/urgent text combined with an unverified external destination
                        (textScore + (maxUrlScore * 0.5f).toInt()).coerceAtMost(98)
                    } else {
                        maxOf(textScore, maxUrlScore)
                    }
                    val finalScore = combinedScore.coerceIn(0, 99)

                    val status = when {
                        finalScore >= 70 || evaluatedUrls.any { it.status == RiskStatus.High } -> RiskStatus.High
                        finalScore >= 35 || evaluatedUrls.any { it.status == RiskStatus.Suspicious } -> RiskStatus.Suspicious
                        else -> RiskStatus.Low
                    }

                    val signals = mutableListOf<String>()
                    keywordReport.topSignals.forEach { signals.add(if (it.detail.isNotBlank()) "${it.title}: ${it.detail}" else it.title) }
                    regionalMatches.take(4).forEach { signals.add("Matched keyword: '$it'") }
                    evaluatedUrls.filter { it.status != RiskStatus.Low }.forEach {
                        signals.add("URL Alert: ${it.title}")
                    }

                    val lower = text.lowercase(Locale.ROOT)
                    val category = when {
                        lower.contains("digital arrest") || lower.contains("डिजिटल अरेस्ट") ||
                                (lower.contains("cbi") && lower.contains("arrest")) ||
                                (lower.contains("सीबीआई") && lower.contains("नोटिस")) -> "DIGITAL ARREST EXTORTION"
                        lower.contains("electricity") || lower.contains("power cut") || lower.contains("bijli") ||
                                lower.contains("बिजली") -> "UTILITY / ELECTRICITY BILL SCAM"
                        lower.contains("kyc") || lower.contains("pan") || lower.contains("aadhaar") ||
                                lower.contains("केवाईसी") -> "BANKING KYC / ACCOUNT BLOCK FRAUD"
                        lower.contains("lottery") || lower.contains("winner") || lower.contains("kbc") -> "LOTTERY & PRIZE SCAM"
                        evaluatedUrls.any { it.title.contains("APK", true) } -> "MALICIOUS APK SIDELOAD TRAP"
                        evaluatedUrls.any { it.title.contains("HOMOGLYPH", true) || it.title.contains("PUNYCODE", true) } -> "HOMOGLYPH DOMAIN SPOOF"
                        finalScore >= 60 -> "SUSPICIOUS COERCIVE REQUEST"
                        finalScore >= 35 -> "UNVERIFIED URGENT MESSAGE"
                        else -> "VERIFIED LOW-RISK MESSAGE"
                    }

                    val explanationEn = when {
                        category.contains("DIGITAL ARREST") ->
                            "Law enforcement agencies (CBI, Police, ED) NEVER conduct arrests, trials, or interrogations via phone or video calls. This is a criminal extortion attempt."
                        category.contains("ELECTRICITY") ->
                            "Power companies never disconnect electricity via urgent WhatsApp/SMS demands with third-party payment links. Contact your electricity board directly."
                        category.contains("KYC") ->
                            "Banks never demand immediate OTP, PIN, or PAN card updates via personal chat links or third-party websites."
                        category.contains("APK") ->
                            "This message instructs you to install an unauthorized app file (.apk) outside Google Play, which can give attackers remote access to your device."
                        status == RiskStatus.High ->
                            "This message displays coercive psychological pressure and unverified requests for funds, personal details, or credentials."
                        status == RiskStatus.Suspicious ->
                            "This message contains unverified urgency or obscured links. Exercise caution before responding or sharing any information."
                        else ->
                            "No known extortion, digital arrest, phishing, or malware patterns were identified in this message."
                    }

                    val explanationHi = when {
                        category.contains("DIGITAL ARREST") ->
                            "कानून प्रवर्तन एजेंसियां (CBI, पुलिस) कभी भी फोन या व्हाट्सएप पर डिजिटल अरेस्ट या पूछताछ नहीं करती हैं। यह जबरन वसूली का प्रयास है।"
                        category.contains("ELECTRICITY") ->
                            "बिजली वितरण कंपनियां व्हाट्सएप पर तत्काल बिजली काटने की धमकी नहीं देती हैं। किसी भी निजी लिंक पर भुगतान न करें।"
                        category.contains("KYC") ->
                            "बैंक कभी भी व्हाट्सएप लिंक के माध्यम से तत्काल केवाईसी, ओटीपी या पिन अपडेट करने के लिए नहीं कहते हैं।"
                        category.contains("APK") ->
                            "यह संदेश आपको गूगल प्ले स्टोर के बाहर अज्ञात ऐप (.apk) डाउनलोड करने के लिए कह रहा है, जिससे आपका फोन हैक हो सकता है।"
                        status == RiskStatus.High ->
                            "इस संदेश में जबरन दबाव और अनधिकृत वित्तीय या व्यक्तिगत जानकारी मांगने के स्पष्ट संकेत मिले हैं।"
                        status == RiskStatus.Suspicious ->
                            "इस संदेश में अपुष्ट दबाव या संदिग्ध लिंक हैं। कोई भी जानकारी साझा करने से पहले सतर्क रहें।"
                        else ->
                            "इस संदेश में कोई ज्ञात धोखाधड़ी, डिजिटल अरेस्ट, फ़िशिंग या दुर्भावनापूर्ण पैटर्न नहीं मिला है।"
                    }

                    UnifiedSecurityAssessment(
                        actualRiskScore = finalScore,
                        status = status,
                        categoryTitle = category,
                        plainExplanationEn = explanationEn,
                        plainExplanationHi = explanationHi,
                        detectedSignals = signals.distinct(),
                        evaluatedUrls = evaluatedUrls,
                        isTextOnly = evaluatedUrls.isEmpty(),
                        rawTextSnippet = text.take(150)
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedSecurityDialog(
    assessment: UnifiedSecurityAssessment,
    onProceedToUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyReport: () -> Unit,
    onAlertFamily: () -> Unit
) {
    var isHindi by remember { mutableStateOf(false) }
    var selectedUrlIndex by remember { mutableStateOf(0) }

    val statusColor = when (assessment.status) {
        RiskStatus.High -> GxDanger
        RiskStatus.Suspicious -> GxWarning
        RiskStatus.Low -> GxSafe
    }
    val softBg = when (assessment.status) {
        RiskStatus.High -> GxDangerSoft
        RiskStatus.Suspicious -> GxWarningSoft
        RiskStatus.Low -> GxSafeSoft
    }

    Card(
        shape = GxShapeMd,
        colors = CardDefaults.cardColors(containerColor = GxSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header Row: Brand, Language Toggle, and Actual Score Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = GxShapePill,
                        color = softBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (assessment.status) {
                                    RiskStatus.High -> Icons.Default.WarningAmber
                                    RiskStatus.Suspicious -> Icons.Default.Security
                                    RiskStatus.Low -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "SUSAGI GUARDIAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GxTextLo,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            assessment.categoryTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Language Switch Pill
                    Surface(
                        shape = GxShapePill,
                        color = GxSurfaceAlt,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier
                            .clickable { isHindi = !isHindi }
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Language", tint = GxPrimary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isHindi) "EN" else "हिन्दी",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxPrimary
                            )
                        }
                    }

                    // Actual Detector Risk Score Badge (NO hardcoded fake percentages)
                    Surface(
                        color = softBg,
                        shape = GxShapePill,
                        border = BorderStroke(0.5.dp, statusColor)
                    ) {
                        Text(
                            text = "${assessment.actualRiskScore}% RISK",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Engine Attribution Pill (Explicit Rule Engine disclosure - zero false claim of Llama)
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(0.5.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ENGINE: ",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextLo
                    )
                    Text(
                        assessment.engineLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GxTextMid
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Plain-Language Human Explanation Card
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (isHindi) "सुरक्षा मार्गदर्शन" else "SECURITY GUIDANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextLo,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isHindi) assessment.plainExplanationHi else assessment.plainExplanationEn,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Detected Evidence Signals
            if (assessment.detectedSignals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isHindi) "पहचाने गए खतरे के संकेत:" else "DETECTED EVIDENCE SIGNALS:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GxTextLo,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    assessment.detectedSignals.take(4).forEach { signal ->
                        Surface(
                            color = GxSurfaceAlt,
                            shape = GxShapeSm,
                            border = BorderStroke(0.5.dp, GxBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "• $signal",
                                fontSize = 11.sp,
                                color = GxTextMid,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Destinations / Links Section (Multi-Link Selection Support)
            if (assessment.evaluatedUrls.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (assessment.evaluatedUrls.size > 1) {
                        if (isHindi) "पहचाने गए गंतव्य (जांच के लिए चुनें):" else "DETECTED DESTINATIONS (SELECT TO INSPECT):"
                    } else {
                        if (isHindi) "वेब गंतव्य:" else "TARGET DESTINATION:"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GxTextLo,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))

                assessment.evaluatedUrls.forEachIndexed { idx, urlVerdict ->
                    val isSelected = (idx == selectedUrlIndex)
                    val urlStatusColor = when (urlVerdict.status) {
                        RiskStatus.High -> GxDanger
                        RiskStatus.Suspicious -> GxWarning
                        RiskStatus.Low -> GxSafe
                    }

                    Surface(
                        color = if (isSelected) GxSurface else GxSurfaceAlt,
                        shape = GxShapeSm,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 0.5.dp,
                            if (isSelected) urlStatusColor else GxBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedUrlIndex = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    LinkCheckActivity.extractHost(urlVerdict.url),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GxTextHi,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    urlVerdict.url.take(65) + if (urlVerdict.url.length > 65) "..." else "",
                                    fontSize = 10.sp,
                                    color = GxTextLo,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Surface(
                                shape = GxShapePill,
                                color = urlStatusColor.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, urlStatusColor)
                            ) {
                                Text(
                                    urlVerdict.status.name.uppercase(Locale.ROOT),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = urlStatusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Action Buttons: Safe Return / Block (Primary), Copy Report, Alert Family
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Safe Action: Dismiss / Block
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assessment.status == RiskStatus.Low) GxSafe else GxDanger,
                        contentColor = Color.White
                    ),
                    shape = GxShapeSm,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        if (assessment.status == RiskStatus.Low) "DISMISS (SAFE)" else "BLOCK & RETURN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Copy Assessment Report
                OutlinedButton(
                    onClick = onCopyReport,
                    border = BorderStroke(1.dp, GxBorder),
                    shape = GxShapeSm,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = GxPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Alert Family
                OutlinedButton(
                    onClick = onAlertFamily,
                    border = BorderStroke(1.dp, GxBorder),
                    shape = GxShapeSm,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Alert Family", tint = GxPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Alert", color = GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Secondary Unsafe Action (Only displayed if URLs exist; never opens automatically)
            if (assessment.evaluatedUrls.isNotEmpty()) {
                val chosenUrl = assessment.evaluatedUrls.getOrNull(selectedUrlIndex)?.url ?: assessment.evaluatedUrls.first().url
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onProceedToUrl(chosenUrl) },
                    border = BorderStroke(0.5.dp, GxBorder),
                    shape = GxShapeSm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Proceed to Destination (Unsafe)",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
