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
    private val known = setOf("pa", "am", "pn", "cu", "tn")
    private val advanceRiskKeywords = Regex("""\b(?:refund|cashback|lottery|reward|kyc|fee|tax|unblock|release|bonus|prize)\b|रिफंड|कैशबैक|इनाम""", RegexOption.IGNORE_CASE)

    // The caller has already validated percent escapes with java.net.URI.
    fun supported(query: String): Boolean {
        val seen = mutableSetOf<String>()
        for (entry in query.splitToSequence('&')) {
            val name = URLDecoder.decode(entry.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
            if (name !in known) continue
            if (!seen.add(name)) return false
            val value = URLDecoder.decode(entry.substringAfter('=', ""), "UTF-8")
            if (value.isBlank() || value.any { it.isISOControl() }) return false
            when (name) {
                "pa" -> if (!payee.matches(value)) return false
                "am" -> if (!amount.matches(value)) return false
                "cu" -> if (value != "INR") return false
            }
        }
        return "pa" in seen
    }

    fun inspectUri(rawUri: String): UpiInspection {
        val trimmed = rawUri.trim()
        val uri = try { URI(trimmed) } catch (_: Exception) {
            return UpiInspection(isValid = false, riskReason = "Invalid URI syntax")
        }
        if (!uri.scheme.equals("upi", ignoreCase = true) || !uri.rawAuthority.equals("pay", ignoreCase = true)) {
            return UpiInspection(isValid = false, riskReason = "Not a standard upi://pay URI")
        }

        val query = uri.rawQuery.orEmpty()
        val paramMap = mutableMapOf<String, MutableList<String>>()
        for (entry in query.splitToSequence('&')) {
            if (entry.isBlank()) continue
            val rawName = entry.substringBefore('=')
            val rawVal = entry.substringAfter('=', "")
            val name = try { URLDecoder.decode(rawName, "UTF-8").lowercase(Locale.ROOT) } catch (_: Exception) { rawName.lowercase(Locale.ROOT) }
            val value = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }
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

        val textToCheck = "${pa.orEmpty()} ${pn.orEmpty()} ${tn.orEmpty()}"
        val isAdvanceRisk = advanceRiskKeywords.containsMatchIn(textToCheck)
        val riskReason = if (isAdvanceRisk) {
            "Reverse UPI debit trap detected. The request claims to be for a refund/cashback/reward, but in UPI you only SEND money to this payee. Entering your PIN will DEDUCT funds."
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
