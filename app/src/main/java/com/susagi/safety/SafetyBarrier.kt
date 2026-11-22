package com.susagi.safety

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Risk { LOW, REVIEW, HIGH, UNKNOWN }
enum class Action { OFFER_OPEN, ASK_CONFIRMATION, RECOMMEND_BLOCK, DO_NOT_OPEN }
enum class Signal(val risk: Risk, val category: String, val explanation: String, val advice: String) {
    SCREEN_CREDENTIAL(Risk.HIGH, "credentials", "The message asks you to share your screen while entering a financial credential.", "End screen sharing before entering any banking code."),
    REFUND_FEE(Risk.HIGH, "advance_fee", "The message asks for an upfront fee to obtain a refund or cashback.", "Check the credit in the official app before taking any action."),
    ADVANCE_FEE(Risk.HIGH, "advance_fee", "The message ties an opportunity, benefit or payout to an advance payment.", "Verify the offer independently before sending money."),
    GUARANTEED_RETURN(Risk.HIGH, "investment", "A deposit request accompanies a promise of guaranteed investment returns.", "Verify the investment and the person offering it independently."),
    REMOTE_ACCESS(Risk.HIGH, "remote_access", "A banking or refund instruction asks you to install remote-access software.", "Close the request and contact the institution through its official app."),
    FAMILY_PAYMENT(Risk.HIGH, "impersonation", "A claimed relative using a different number requests an urgent transfer.", "Call the relative on a previously saved number before paying."),
    FASTAG(Risk.HIGH, "fastag", "A threatened FASTag restriction accompanies a payment request and a supplied link.", "Check your tag balance directly with the issuer."),
    INVALID(Risk.UNKNOWN, "input", "The destination or input could not be assessed.", "Use the official app or enter the address yourself."),
    LIMIT(Risk.UNKNOWN, "input", "The input exceeds a local inspection limit.", "Check individual destinations separately."),
    HTTP(Risk.REVIEW, "transport", "The URL uses an unencrypted HTTP connection.", "Use the official HTTPS address before entering sensitive information."),
    PAYMENT(Risk.REVIEW, "payment", "This URI requests a UPI payment.", "Check the payee and amount in your UPI app."),
    USERINFO(Risk.HIGH, "phishing", "User information precedes the destination host.", "Close the link and open the official service directly."),
    BRAND(Risk.HIGH, "impersonation", "The destination uses a bank name or lookalike outside its listed domain.", "Open the bank app independently."),
    MIXED(Risk.REVIEW, "lookalike", "A hostname label mixes Latin and visually similar scripts.", "Verify the spelling against a saved official address."),
    APK(Risk.HIGH, "software", "The destination path names an Android installation package.", "Leave the download closed and use the official app store."),
    REDIRECT(Risk.REVIEW, "redirect", "A redirect parameter supplies a different or unverified destination.", "Use the intended service directly."),
    NOTICE(Risk.HIGH, "government_impersonation", "A purported government notice directs payment through a supplied link.", "Verify the notice independently on the official portal."),
    FEE(Risk.HIGH, "delivery", "A parcel notice requests a fee through a link.", "Check the notice using the issuer's official service."),
    SHORT(Risk.REVIEW, "redirect", "A short link conceals its final destination.", "Ask for the full address."),
    IP(Risk.REVIEW, "destination", "The address identifies an IP endpoint directly.", "Verify who operates the endpoint."),
    UPI(Risk.HIGH, "payment", "A promised credit is tied to a PIN, QR scan or collect approval.", "Decline the request and check incoming funds in your UPI app."),
    KYC(Risk.HIGH, "banking", "An account restriction is paired with a KYC or banking update request.", "Check KYC status in the official bank app."),
    ARREST(Risk.HIGH, "extortion", "A law-enforcement or custody claim accompanies a demand for payment, secrecy or control of the call.", "End the interaction and contact the authority independently."),
    UTILITY(Risk.HIGH, "utility_extortion", "A power cutoff threat is paired with a payment demand and a link.", "Check the outstanding bill directly with your electricity provider."),
    OTP(Risk.HIGH, "credentials", "The sender asks you to disclose a verification credential.", "Keep the code private and contact your bank independently.")
}
data class Evidence(val id: Signal, val host: String? = null)
data class SharedDestination(val originalUrl: String, val assessment: Assessment)
class Assessment internal constructor(val risk: Risk, val evidence: List<Evidence>, val destinationHost: String?, val action: Action, val destinations: List<SharedDestination> = emptyList()) {
    val source = "OFFLINE_RULES"
    val categories = evidence.map { it.id.category }.distinct()
    val explanation = evidence.map { it.id.explanation }.distinct().joinToString(" ").ifEmpty { "No configured local rule matched." }
    val recommendations = evidence.map { it.id.advice }.distinct().ifEmpty { listOf("Verify unexpected requests independently.") }
    val uncertainty = "Offline inspection cannot establish ownership, reputation or the destination after navigation."
}

/** Pure inspection. Callers retain the original URI and decide whether to present a continuation. */
object SafetyBarrier {
    private const val MAX = 32768
    private val banks = mapOf("axisbank" to listOf("axisbank.com"), "sbi" to listOf("sbi.co.in","onlinesbi.sbi","sbi.bank.in"),
        "hdfc" to listOf("hdfcbank.com","hdfcbank.bank.in"), "icici" to listOf("icicibank.com","icici.bank.in"),
        "rbi" to listOf("rbi.org.in"), "pnb" to listOf("pnbindia.in"))
    private val links = Regex("""(?:[a-z][a-z0-9+.-]{0,31}://|(?:mailto|tel|javascript|data):|www\.)[^\s<>"\p{Cc}]*""", RegexOption.IGNORE_CASE)
    private val authority = Regex("""\b(?:police|cbi|fir|warrant|arrest|ed officer|enforcement directorate|customs|narcotics)\b|पुलिस|गिरफ्तार|वारंट|प्रवर्तन निदेशालय|कस्टम|सीबीआई""")
    private val coercion = Regex("""\b(?:transfer|pay|skype|wire|fine)\b|video call|do not disconnect|stay on the line|avoid detention|avoid arrest|safe account|पैसे भेज|पैसे ट्रांसफर|जुर्माना|सुरक्षा खाते""")
    private val credit = Regex("""refund|\breceive\b|cashback|paane|paisa lene|रिफंड|पाने|प्राप्त""")
    private val approve = Regex("""\b(?:enter|approve|accept|scan|daalo|dalo|karo)\b|दर्ज|डालें|स्कैन""")
    private val payment = Regex("""\b(?:pin|qr|collect request)\b|पिन|क्यूआर""")
    private val credentials = Regex("""\b(?:otp|cvv|upi pin|atm pin|net[- ]banking password|authentication code|verification code|one[- ]time password)\b|ओटीपी""")
    private val disclose = Regex("""\b(?:share|send|tell|disclose|forward|batao|bhejo|batana|bataye)\b|भेजें|बताएं|बताइए""")
    private val warning = Regex("""\b(?:never|do not|don't|must not|will never|alert from rbi)\s+(?:ask you to\s+)?(?:share|tell|disclose|enter|approve|scan|send|forward|transfer|pay|demand)\b|नहीं मांगता""")
    private val negativeHindi = Regex("""(?:मत|नहीं|न)\s+(?:भेजें|बताएं|बताइए|करें|डालें|मांगता)|(?:share|send|scan|bhejo|batao)\s+(?:mat|nahi)\s*(?:karo|karna)?|(?:mat|nahi)\s+(?:bhejo|batao|batana|bataye|karo)""")
    private val splitClause = Regex("""[.!?;।\n]+""")
    private val arrestIsolation = Regex("""\b(?:custody|digital arrest|giraftar)\b|हिरासत|डिजिटल अरेस्ट|गिरफ्तारी से बचने""")
    private val secrecyDemand = Regex("""do not hang up|do not tell anyone|do not speak to|do not contact|kisi ko mat batana|किसी को मत बताना|गोपनीय रखें|संपर्क न करें""")
    private val detentionClaim = Regex("""you (?:are|will be) (?:under (?:digital )?arrest|in custody|detained)|warrant (?:against you|for your arrest)|drugs (?:found|in your parcel)|contraband|under investigation|custody warrant|नशीले पदार्थ|आपको गिरफ्तार|आप (?:हिरासत|डिजिटल अरेस्ट) में|जांच के दायरे में|aap (?:giraftar|digital arrest)""")
    private val investigationClaim = Regex("""\b(?:under investigation|investigation against you|account under investigation|bank is under investigation)\b|जांच चल रही|खाता जांच|जांच शुरू""")
    private val paymentApproval = Regex("""approve (?:the |this )?(?:mandate|request)|mandate accept|कलेक्ट अनुरोध स्वीकार""")
    private val banking = Regex("""\b(?:kyc|pan card|bank account)\b|केवाईसी|खाता""")
    private val blocked = Regex("""block|suspend|deactivat|unfreeze|expire|बंद|ब्लॉक|सस्पेंड""")
    private val update = Regex("""updat|submit|verify|click|अपडेट|जमा|वेरिफाई""")
    private val delivery = Regex("""parcel|courier|customs|पार्सल""")
    private val utility = Regex("""electricity|bijli|बिजली""")
    private val cutoff = Regex("""disconnect|power cut|cut off|काट|कट जाएगी""")
    private val government = Regex("""government notice|challan|चालान|सरकारी नोटिस""")
    private val fee = Regex("""\b(?:pay|deposit|bharo)\b|भुगतान|चुकाएं|भरें""")

    private fun inspectInput(url: String): Assessment =
        if (url.length > MAX) assess(listOf(Evidence(Signal.LIMIT))) else inspect(url.trim())

    suspend fun analyzeUrl(url: String): Assessment = withContext(Dispatchers.Default) { inspectInput(url) }
    suspend fun analyzeMessage(text: String): Assessment = analyze(null, text)
    suspend fun analyze(url: String?, text: String?): Assessment = withContext(Dispatchers.Default) {
        val direct = url?.let(::inspectInput)
        val evidence = direct?.evidence.orEmpty().toMutableList()
        val raw = text.orEmpty()
        if (raw.length > MAX) evidence.add(Evidence(Signal.LIMIT))
        val bounded = raw.take(MAX)
        val destinations = links.findAll(bounded).distinctBy { it.value }.map { match ->
            val original = match.value
            val assessment = if (bounded.length < raw.length && match.range.last == bounded.lastIndex && !raw[MAX].isWhitespace()) {
                assess(listOf(Evidence(Signal.LIMIT)))
            } else inspect(original)
            evidence += assessment.evidence
            SharedDestination(original, assessment)
        }.toList()
        val normalized = Normalizer.normalize(links.replace(bounded, " "), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace('’', '\'').replace("\u200b","").replace("\ufeff","")
            .replace("\u2060", "").replace("\u00ad", "")
        val request = negativeHindi.replace(warning.replace(normalized, ""), "")
        fun add(signal: Signal) { evidence.add(Evidence(signal)) }
        val clauses = splitClause.split(normalized).filter { it.isNotBlank() }
        clauses.forEachIndexed { index, clause ->
            val nextClause = clauses.getOrNull(index + 1)
            ContextualScamRules.inspect(clause, destinations.isNotEmpty() || direct != null, nextClause).forEach(::add)
        }
        // A routine mention of police must not lend authority to an unrelated payment instruction.
        val arrestDemand = clauses.withIndex().any { (index, clause) ->
            val actionable = negativeHindi.replace(warning.replace(clause, ""), "")
            val sameClause = authority.containsMatchIn(clause) && coercion.containsMatchIn(actionable) ||
                arrestIsolation.containsMatchIn(clause) && secrecyDemand.containsMatchIn(clause) ||
                detentionClaim.containsMatchIn(clause) && coercion.containsMatchIn(actionable)
            val next = clauses.getOrNull(index + 1).orEmpty()
            val nextActionable = negativeHindi.replace(warning.replace(next, ""), "")
            sameClause || ((detentionClaim.containsMatchIn(clause) || arrestIsolation.containsMatchIn(clause) || investigationClaim.containsMatchIn(clause)) &&
                (coercion.containsMatchIn(nextActionable) || secrecyDemand.containsMatchIn(next)))
        }
        if (arrestDemand) add(Signal.ARREST)
        val requestsPaymentApproval = payment.containsMatchIn(normalized) && approve.containsMatchIn(request)
        val requestsUpiMandate = "upi" in normalized && paymentApproval.containsMatchIn(request)
        if (credit.containsMatchIn(normalized) && (requestsPaymentApproval || requestsUpiMandate)) add(Signal.UPI)
        if (banking.containsMatchIn(normalized) && blocked.containsMatchIn(normalized) && update.containsMatchIn(request)) add(Signal.KYC)
        val credentialRequest = reportedExample.replace(request, " ")
        if (CredentialIntent.inspect(reportedExample.replace(normalized, " ")).any { it.context == CredentialIntent.Context.REQUEST } ||
            splitClause.split(credentialRequest).any { credentials.containsMatchIn(it) && disclose.containsMatchIn(it) }) add(Signal.OTP)
        if (fee.containsMatchIn(request) && (direct != null || destinations.isNotEmpty())) {
            if (utility.containsMatchIn(normalized) && cutoff.containsMatchIn(normalized)) add(Signal.UTILITY)
            if (government.containsMatchIn(normalized)) add(Signal.NOTICE)
            if (delivery.containsMatchIn(normalized)) add(Signal.FEE)
        }
        if (bounded.isBlank() && direct == null) add(Signal.INVALID)
        assess(evidence, direct?.destinationHost, destinations)
    }

    private fun assess(items: List<Evidence>, host: String? = null, destinations: List<SharedDestination> = emptyList()): Assessment {
        val evidence = items.distinct()
        val risks = evidence.map { it.id.risk }
        val risk = listOf(Risk.HIGH, Risk.UNKNOWN, Risk.REVIEW).firstOrNull { it in risks } ?: Risk.LOW
        val action = when {
            Risk.UNKNOWN in risks -> Action.DO_NOT_OPEN
            risk == Risk.HIGH -> Action.RECOMMEND_BLOCK
            risk == Risk.REVIEW -> Action.ASK_CONFIRMATION
            else -> Action.OFFER_OPEN
        }
        return Assessment(risk, evidence, host, action, destinations)
    }

    // Limit the exception to a directly attributed, closed quotation. Later demands still count.
    private val reportedExample = Regex("""\b(?:scammers (?:say|write)|example of a scam message)\s*:?\s*(?:"[^"\n]{1,200}"|“[^”\n]{1,200}”)""")

    private class RedirectBudget(var remaining: Int = 8)

    private fun inspect(raw: String, depth: Int = 0, budget: RedirectBudget = RedirectBudget()): Assessment {
        fun invalid() = assess(listOf(Evidence(Signal.INVALID)))
        if (raw.length > MAX) return assess(listOf(Evidence(Signal.LIMIT)))
        val uri = try { URI(if (raw.startsWith("www.", true)) "https://$raw" else raw) } catch (_: Exception) { return invalid() }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == "upi") {
            if (!uri.rawAuthority.equals("pay", true) || !uri.rawPath.isNullOrEmpty() || uri.rawFragment != null) return invalid()
            if (!PaymentFields.supported(uri.rawQuery.orEmpty())) return invalid()
            return assess(listOf(Evidence(Signal.PAYMENT)))
        }
        if (scheme !in listOf("http","https")) return invalid()
        val auth = uri.rawAuthority ?: return invalid()
        val endpoint = auth.substringAfterLast('@')
        val host = if (endpoint.startsWith("[")) uri.host ?: return invalid() else {
            val name = endpoint.substringBefore(':').removeSuffix(".")
            val port = endpoint.substringAfter(':', "")
            if (':' in endpoint && (port.isEmpty() || !port.all { it in '0'..'9' } || port.toIntOrNull() !in 1..65535)) return invalid()
            try { IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) } catch (_: IllegalArgumentException) { return invalid() }
        }
        if (uri.port > 65535 || host.length !in 1..253 || host.split('.').any { it.isEmpty() || (it.startsWith("xn--") && IDN.toUnicode(it) == it) }) return invalid()
        val evidence = mutableListOf<Evidence>()
        fun add(id: Signal) { evidence.add(Evidence(id,host)) }
        if (scheme == "http") add(Signal.HTTP)
        if ('@' in auth) add(Signal.USERINFO)
        if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) add(Signal.IP)
        fun below(root: String) = host == root || host.endsWith(".$root")
        if (listOf("bit.ly","t.co","tinyurl.com","rb.gy").any(::below)) add(Signal.SHORT)
        val decoded = IDN.toUnicode(host)
        val skeleton = decoded.replace('а','a').replace('і','i').replace('х','x').replace('с','c').replace('о','o')
        if (banks.any { (brand, roots) ->
                val labels = skeleton.replace('1', 'i').replace('0', 'o').split('.','-')
                val looksLike = labels.any { it == brand || it.contains(brand + "bank") || it.endsWith(brand) } ||
                    roots.any { skeleton.contains(it) }
                looksLike && roots.none(::below)
            }) add(Signal.BRAND)
        if (decoded.split('.').any { label ->
                label.any { it in 'a'..'z' } && label.any { it.code in 0x370..0x52f }
            }) add(Signal.MIXED)
        if (uri.path.orEmpty().substringAfterLast('/').substringBefore(';').endsWith(".apk", true)) add(Signal.APK)
        if (uri.rawQuery != null) {
            uri.rawQuery.orEmpty().split('&').forEach { entry ->
                if (URLDecoder.decode(entry.substringBefore('='), "UTF-8").lowercase(Locale.ROOT) in listOf("redirect","redirect_uri","returnurl","next","url","target")) {
                    if (depth >= 3 || budget.remaining == 0) {
                        if (evidence.none { it.id == Signal.LIMIT }) add(Signal.LIMIT)
                        return@forEach
                    }
                    budget.remaining--
                    val value = URLDecoder.decode(entry.substringAfter('=', ""), "UTF-8")
                    val dest = try { uri.resolve(value).toString() } catch (_: IllegalArgumentException) { "" }
                    val target = inspect(dest, depth + 1, budget)
                    if (target.destinationHost != host || target.evidence.isNotEmpty()) {
                        add(Signal.REDIRECT)
                        evidence += target.evidence
                    }
                }
            }
        }
        return assess(evidence,host)
    }
}
