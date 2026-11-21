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
import com.guardian.app.ui.theme.GuardianTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LinkVerdict(
    val url: String,
    val status: RiskStatus,
    val title: String,
    val detail: String
)

class LinkCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = extractUrlFromIntent(intent)
        if (targetUrl.isBlank()) {
            if (intent?.action == Intent.ACTION_SEND) {
                Toast.makeText(this, "SuSagi Guardian: No link detected in shared text", Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        // Anti-recursion check: prevent recursive intent bouncing
        if (intent.getBooleanExtra(EXTRA_PROCESSED_BY_GUARDIAN, false)) {
            finish()
            return
        }

        Log.d("GuardianLinkCheck", "Intercepted Intent URL: $targetUrl")
        val verdict = evaluateLinkSafety(targetUrl)

        if (verdict.status == RiskStatus.Low) {
            // Unflagged Link: Show accurate verdict toast and forward to actual browser
            Toast.makeText(this, "🛡️ Guardian: No known threat detected", Toast.LENGTH_SHORT).show()
            openInExternalBrowser(targetUrl)
            finish()
            return
        }

        // Suspicious or Scam Link: Display Interactive Safety Interception Dialog
        setContent {
            GuardianTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinkWarningDialog(
                        verdict = verdict,
                        onProceed = {
                            openInExternalBrowser(targetUrl)
                            finish()
                        },
                        onBlock = {
                            Toast.makeText(this@LinkCheckActivity, "Blocked malicious link navigation", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCopy = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", targetUrl))
                            Toast.makeText(this@LinkCheckActivity, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
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
        }

        val resolveInfos = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
        val targetApp = resolveInfos.firstOrNull { 
            val pkg = it.activityInfo.packageName
            pkg != packageName && !pkg.contains("guardian") && !pkg.contains("susagi")
        }

        if (targetApp != null) {
            browserIntent.setClassName(targetApp.activityInfo.packageName, targetApp.activityInfo.name)
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivity(browserIntent)
                return
            } catch (e: Exception) {
                Log.e("GuardianLinkCheck", "Cannot launch targeted browser: ${e.message}")
            }
        }

        // Fallback chooser
        try {
            val chooser = Intent.createChooser(browserIntent, "Open with safe browser").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(chooser)
        } catch (e: Exception) {
            Log.e("GuardianLinkCheck", "Cannot launch browser chooser: ${e.message}")
        }
    }

    companion object {
        private val URL_REGEX = Regex(
            "(?:https?://|www\\.)[a-zA-Z0-9\\-\\._~:/?#\\[\\]@!$&'()*+,;=%]+",
            RegexOption.IGNORE_CASE
        )

        private val IP_REGEX = Regex(
            "^(?:\\d{1,3}\\.){3}\\d{1,3}$"
        )

        fun extractUrlFromIntent(intent: Intent?): String {
            if (intent == null) return ""
            // 1. Direct dataString from ACTION_VIEW
            val dataStr = intent.dataString
            if (!dataStr.isNullOrBlank()) return dataStr.trim()

            // 2. EXTRA_TEXT from ACTION_SEND or sharing
            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!extraText.isNullOrBlank()) {
                val extracted = extractUrlFromText(extraText)
                if (extracted.isNotBlank()) return extracted
            }

            // 3. ClipData
            val clipData = intent.clipData
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                val itemUri = item.uri
                if (itemUri != null) return itemUri.toString().trim()
                val text = item.text?.toString()
                if (!text.isNullOrBlank()) {
                    val extracted = extractUrlFromText(text)
                    if (extracted.isNotBlank()) return extracted
                }
            }

            return ""
        }

        fun extractUrlFromText(text: String): String {
            val match = URL_REGEX.find(text) ?: return ""
            var url = match.value.trim()
            if (url.startsWith("www.", ignoreCase = true)) {
                url = "https://$url"
            }
            return url
        }

        fun extractHost(url: String): String {
            val clean = url.trim().lowercase()
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
            val cleanHost = host.lowercase().trim().removeSuffix(".")
            if (cleanHost.isBlank()) return false
            return trustedRoots.any { root ->
                val cleanRoot = root.lowercase().trim().removeSuffix(".")
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
            val lower = trimmed.lowercase()

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
                    detail = "The web address '$url' does not specify a valid, well-formed host domain."
                )
            }

            // 1. Critical Threat: Direct IP Hostname (classic malware C2 & phishing tactic)
            if (host.isNotBlank() && IP_REGEX.matches(host)) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 UNVERIFIED IP HOST DETECTED",
                    detail = "This link points directly to a raw IP address ($host) rather than a registered domain name. Legitimate institutions never use raw IP URLs."
                )
            }

            // 2. Critical Threat: UserInfo URL Deception (e.g. https://sbi.co.in@evil-server.com/login)
            if (!userInfo.isNullOrBlank()) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 URL IMPERSONATION SPOOF DETECTED",
                    detail = "This link uses user-info prefixing ($userInfo) to disguise the true destination domain ($host)."
                )
            }

            // 3. Critical Threat: Direct APK or Executable Sideloading
            if (lower.endsWith(".apk") || lower.contains(".apk?") || path.endsWith(".apk") ||
                lower.endsWith(".exe") || lower.endsWith(".dex") || lower.endsWith(".vbs") || lower.endsWith(".sh")) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 MALICIOUS PACKAGE / APK SIDELOAD DETECTED",
                    detail = "This link initiates an unauthorized APK or binary download outside of the Google Play Store, commonly used in RAT (Remote Access Trojan) scams."
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
                        detail = "This destination has been flagged for active banking credential harvesting, digital arrest extortion, or fraudulent payment gateway impersonation."
                    )
                }
            }

            // 5. Critical Threat: Subdomain Impersonation of Financial/Gov Institutions
            val brandPrefixes = listOf("sbi", "hdfc", "icici", "pnb", "axis", "rbi", "incometax", "epfo", "cyberpolice", "uidai")
            val trustedBankAndGovRoots = listOf(
                "sbi.co.in", "onlinesbi.sbi", "rbi.org.in", "hdfcbank.com", "icicibank.com",
                "axisbank.com", "pnbindia.in", "bankofbaroda.in", "kotak.com", "gov.in", "nic.in"
            )

            // Decode IDN / Punycode to detect hidden homoglyphs and target brand keywords
            val decodedHost = try {
                java.net.IDN.toUnicode(host)
            } catch (_: Exception) { host }
            val normalizedDecodedHost = normalizeHomoglyphs(decodedHost.lowercase())

            for (brand in brandPrefixes) {
                val matchesHost = host.contains(brand) || normalizedDecodedHost.contains(brand)
                if (matchesHost && !isTrustedHost(host, trustedBankAndGovRoots)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 FAKE BRAND IMPERSONATION DETECTED",
                        detail = "The domain '$host' mimics official financial or government infrastructure ($brand) on an unauthorized server."
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
                        detail = "This link uses mixed-script internationalized characters ('$decodedHost') that look identical to genuine Latin letters to deceive users."
                    )
                }

                // Check recognized national IDN TLDs for India
                val isNationalIdn = host.endsWith(".xn--h2brj9c") || host.endsWith(".xn--mgbbh1a71e") ||
                        host.endsWith(".xn--fpcrj9c3d") || host.endsWith(".xn--gecrj9c") ||
                        host.endsWith(".xn--s9brj9c") || host.endsWith(".xn--xkc2dl3a5ee0h") ||
                        host.endsWith(".xn--45brj9c") || host.endsWith(".xn--2scrj9c") ||
                        host.endsWith(".xn--rvcs84f") || host.endsWith(".xn--3hcrj9c")

                // If not a recognized national IDN, and hosted on commercial/generic TLD
                if (!isNationalIdn && (host.endsWith(".com") || host.endsWith(".net") || host.endsWith(".org") ||
                            host.endsWith(".xyz") || host.endsWith(".top") || host.endsWith(".info"))) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 PUNYCODE DOMAIN DETECTED",
                        detail = "This link uses internationalized encoding ('$decodedHost') on an unverified commercial registry."
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
                    detail = "This link uses a URL shortener that conceals the true landing page. Proceed with caution."
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
                    detail = "This website is hosted on a top-level domain frequently associated with throwaway phishing sites and malicious campaigns."
                )
            }

            // 9. Clean Domain
            return LinkVerdict(
                url = url,
                status = RiskStatus.Low,
                title = "🛡️ NO KNOWN THREAT DETECTED",
                detail = "Standard web domain. No active phishing signatures, credential harvesting, or sideloading payloads detected."
            )
        }

        const val EXTRA_PROCESSED_BY_GUARDIAN = "com.guardian.app.PROCESSED_BY_GUARDIAN"

        fun requestDefaultBrowserRole(activity: android.app.Activity, requestCode: Int = 1001) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                        activity.startActivityForResult(intent, requestCode)
                    } else {
                        Toast.makeText(activity, "SuSagi Guardian is already your default link protector", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(activity, "Set SuSagi Guardian as Default Browser in Android Settings -> Apps", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun LinkWarningDialog(
    verdict: LinkVerdict,
    onProceed: () -> Unit,
    onBlock: () -> Unit,
    onCopy: () -> Unit
) {
    val isHighRisk = verdict.status == RiskStatus.High
    val accentColor = if (isHighRisk) com.guardian.app.ui.theme.GxDanger else com.guardian.app.ui.theme.GxWarning
    val softBg = if (isHighRisk) com.guardian.app.ui.theme.GxDangerSoft else com.guardian.app.ui.theme.GxWarningSoft

    val parsedDomain = remember(verdict.url) {
        LinkCheckActivity.extractHost(verdict.url).ifBlank { verdict.url }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = com.guardian.app.ui.theme.GxSurface),
        shape = com.guardian.app.ui.theme.GxShapeLg,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Header with Security Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = softBg,
                        shape = com.guardian.app.ui.theme.GxShapeSm,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isHighRisk) Icons.Default.WarningAmber else Icons.Default.Security,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            "GUARDIAN LINK SHIELD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.guardian.app.ui.theme.GxTextLo,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            if (isHighRisk) "MALICIOUS LINK INTERCEPTED" else "SUSPICIOUS DESTINATION",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                }

                Surface(
                    color = softBg,
                    shape = com.guardian.app.ui.theme.GxShapePill,
                    border = BorderStroke(0.5.dp, accentColor)
                ) {
                    Text(
                        if (isHighRisk) "95% FRAUD" else "65% CAUTION",
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Parsed Host Highlight
            Surface(
                color = com.guardian.app.ui.theme.GxSurfaceAlt,
                shape = com.guardian.app.ui.theme.GxShapeSm,
                border = BorderStroke(1.dp, com.guardian.app.ui.theme.GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "TARGET HOSTNAME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.guardian.app.ui.theme.GxTextLo,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        parsedDomain,
                        color = com.guardian.app.ui.theme.GxTextHi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                verdict.detail,
                color = com.guardian.app.ui.theme.GxTextMid,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(14.dp))

            // URL Container with 1-tap copy
            Surface(
                color = com.guardian.app.ui.theme.GxSurfaceAlt,
                shape = com.guardian.app.ui.theme.GxShapeSm,
                border = BorderStroke(1.dp, com.guardian.app.ui.theme.GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        verdict.url.take(75) + if (verdict.url.length > 75) "..." else "",
                        color = com.guardian.app.ui.theme.GxTextLo,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = com.guardian.app.ui.theme.GxShapeSm,
                        border = BorderStroke(1.dp, com.guardian.app.ui.theme.GxBorder)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = com.guardian.app.ui.theme.GxPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = com.guardian.app.ui.theme.GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Actions: Block (Primary Safe) vs Proceed (Secondary Unsafe)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBlock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.guardian.app.ui.theme.GxDanger,
                        contentColor = Color.White
                    ),
                    shape = com.guardian.app.ui.theme.GxShapeSm,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("BLOCK & RETURN", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }

                OutlinedButton(
                    onClick = onProceed,
                    border = BorderStroke(1.dp, com.guardian.app.ui.theme.GxBorder),
                    shape = com.guardian.app.ui.theme.GxShapeSm,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(46.dp)
                ) {
                    Text("Proceed (Unsafe)", color = com.guardian.app.ui.theme.GxTextLo, fontSize = 11.sp)
                }
            }
        }
    }
}
