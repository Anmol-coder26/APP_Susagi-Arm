package com.susagi.safety

import java.net.URLDecoder

internal object PaymentFields {
    private val payee = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*@[A-Za-z0-9][A-Za-z0-9.-]*""")
    private val amount = Regex("""[0-9]{1,12}(?:\.[0-9]{1,2})?""")
    private val known = setOf("pa", "am", "pn", "cu", "tn")

    // The caller has already validated percent escapes with java.net.URI.
    fun supported(query: String): Boolean {
        val seen = mutableSetOf<String>()
        for (entry in query.splitToSequence('&')) {
            val name = URLDecoder.decode(entry.substringBefore('='), "UTF-8").lowercase(java.util.Locale.ROOT)
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
}
