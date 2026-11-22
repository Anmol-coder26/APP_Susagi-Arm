package com.susagi.safety

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

data class UpiInspection(
    val isValid: Boolean,
    val payeeVpa: String? = null,
    val payeeName: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val note: String? = null,
    val duplicateKeys: List<String> = emptyList(),
    val isAdvanceFeeOrReverseDebit: Boolean = false,
    val riskReason: String? = null,
    val unverifiedDisplayName: String? = null
)

object PaymentFields {
    private val payee = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*@[A-Za-z0-9][A-Za-z0-9.-]*""")
    private val amount = Regex("""[0-9]{1,12}(?:\.[0-9]{1,2})?""")
    private const val MAX_URI = 8192
    private val creditInstruction = Regex(
        """\b(?:claim|collect|receive|activate)\s+(?:(?:your|the|a)\s+)?(?:cashback|refund|reward|prize)\b|(?:refund|cashback)\s+(?:paane|lene) ke liye""",
        RegexOption.IGNORE_CASE
    )
    private val informational = Regex("""\b(?:never|do not|already credited|scam example|scammer asked)\b""", RegexOption.IGNORE_CASE)
    private val clauseBoundary = Regex("""[.;!?।\n]+|\b(?:instead|however|but|lekin)\b""", RegexOption.IGNORE_CASE)

    fun supported(query: String): Boolean =
        query.length <= MAX_URI && inspectUri("upi://pay?" + query).isValid

    fun inspectUri(rawUri: String): UpiInspection = inspectUri(rawUri, "")

    fun inspectUri(rawUri: String, accompanyingText: String): UpiInspection {
        if (rawUri.length > MAX_URI || accompanyingText.length > 32768)
            return UpiInspection(false, riskReason = "Payment input exceeds the offline inspection limit.")
        val trimmed = rawUri.trim()
        val uri = try { URI(trimmed) } catch (_: Exception) {
            return UpiInspection(isValid = false, riskReason = "Invalid URI syntax")
        }
        if (!uri.scheme.equals("upi", ignoreCase = true) || !uri.rawAuthority.equals("pay", ignoreCase = true)) {
            return UpiInspection(isValid = false, riskReason = "Not a standard upi://pay URI")
        }

        if (!uri.rawPath.isNullOrEmpty() || uri.rawFragment != null)
            return UpiInspection(false, riskReason = "Unexpected payment path or fragment.")
        val query = uri.rawQuery.orEmpty()
        val paramMap = mutableMapOf<String, MutableList<String>>()
        for (entry in query.splitToSequence('&')) {
            if (entry.isBlank()) continue
            val rawName = entry.substringBefore('=')
            val rawVal = entry.substringAfter('=', "")
            val name = URLDecoder.decode(rawName, "UTF-8").lowercase(Locale.ROOT)
            val value = URLDecoder.decode(rawVal, "UTF-8")
            if (name.isBlank() || (name in setOf("pa", "am", "pn", "cu", "tn") && value.isBlank()) ||
                value.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() || it == '\uFFFD' })
                return UpiInspection(false, riskReason = "Invalid or hidden payment field.")
            paramMap.getOrPut(name) { mutableListOf() }.add(value)
        }

        val duplicateKeys = paramMap.filter { it.value.size > 1 }.keys.toList()
        val paList = paramMap["pa"].orEmpty()
        val amList = paramMap["am"].orEmpty()
        val pnList = paramMap["pn"].orEmpty()
        val cuList = paramMap["cu"].orEmpty()
        val tnList = paramMap["tn"].orEmpty()

        if (duplicateKeys.isNotEmpty()) {
            return UpiInspection(
                isValid = false,
                duplicateKeys = duplicateKeys,
                riskReason = "Conflicting or duplicate parameters detected: ${duplicateKeys.joinToString(", ")}. Potential parameter pollution attack."
            )
        }

        val pa = paList.firstOrNull()
        if (pa.isNullOrBlank() || !payee.matches(pa)) {
            return UpiInspection(isValid = false, riskReason = "Missing or invalid payee VPA address (pa).")
        }

        val am = amList.firstOrNull()
        if (am != null && !amount.matches(am)) {
            return UpiInspection(isValid = false, payeeVpa = pa, riskReason = "Invalid payment amount format (am).")
        }

        val cu = cuList.firstOrNull()
        if (cu != null && cu != "INR") {
            return UpiInspection(isValid = false, payeeVpa = pa, riskReason = "Unsupported currency ($cu). Only INR is supported.")
        }

        val pn = pnList.firstOrNull()
        val tn = tnList.firstOrNull()

        val isAdvanceRisk = sequenceOf(tn.orEmpty(), accompanyingText).any { source ->
            clauseBoundary.split(source).any { clause ->
                creditInstruction.containsMatchIn(clause) && !informational.containsMatchIn(clause)
            }
        }
        val riskReason = if (isAdvanceRisk) {
            "Reverse UPI debit trap indicator: the wording invites you to claim a credit, while this URI initiates a payment. Verify the transaction direction before approval."
        } else null

        val unverifiedName = pn?.let { "Unverified Payee: $it ($pa)" } ?: "Unverified Payee: $pa"

        return UpiInspection(
            isValid = true,
            payeeVpa = pa,
            payeeName = pn,
            amount = am,
            currency = cu ?: "INR",
            note = tn,
            duplicateKeys = emptyList(),
            isAdvanceFeeOrReverseDebit = isAdvanceRisk,
            riskReason = riskReason,
            unverifiedDisplayName = unverifiedName
        )
    }
}
