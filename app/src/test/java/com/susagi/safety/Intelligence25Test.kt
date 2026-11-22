package com.susagi.safety

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Intelligence25Test {
    @Test fun creditClaimsRequireInstruction() {
        val pay = "upi://pay?pa=refunddesk@bank&am=12"
        for (context in listOf("To receive your refund, approve this.", "Cashback paane ke liye scan karo.")) {
            assertTrue(context, PaymentFields.inspectUri(pay, context).isAdvanceFeeOrReverseDebit)
        }
        for (context in listOf("Refund already credited.", "Never scan to claim cashback.", "School fee receipt.")) {
            assertFalse(context, PaymentFields.inspectUri(pay, context).isAdvanceFeeOrReverseDebit)
        }
        assertTrue(PaymentFields.inspectUri(pay, "Do not pay the caller; instead claim your refund here.").isAdvanceFeeOrReverseDebit)
    }

    @Test fun paymentEntryPointsAgree() {
        for (query in listOf("pa=a@bank&pn=%00", "pa=a@bank&pn=%E2%80%AEabc", "pa=a@bank&%61m=2&am=3", "pa=a@bank&pn=%", "pa=a@bank&tn=")) {
            assertFalse(query, PaymentFields.supported(query))
            assertFalse(query, PaymentFields.inspectUri("upi://pay?$query").isValid)
        }
        assertTrue(PaymentFields.supported("pa=a%40bank&tn=Food%20%26%20tea"))
        assertFalse(PaymentFields.inspectUri("upi://pay?pa=a@bank&tn=" + "x".repeat(9000)).isValid)
    }

    @Test fun familyEvidenceRequiresPaymentDemand() {
        fun inspect(text: String) = ContextualScamRules.inspect(text, false)
        assertFalse(Signal.FAMILY_PAYMENT in inspect("mom this is my new number call me urgently"))
        assertTrue(Signal.FAMILY_PAYMENT in inspect("mom this is my new number transfer money immediately"))
    }

    @Test fun distinguishDisclosureFromAdvice() = runBlocking {
        val warning = "Never read out the six-digit code."
        val report = "The caller asked me to read out the six-digit code."
        val demand = "Read me back the six-digit code."
        assertEquals(CredentialIntent.Context.WARNING, CredentialIntent.inspect(warning).single().context)
        assertEquals(CredentialIntent.Context.ATTRIBUTED, CredentialIntent.inspect(report).single().context)
        assertFalse(Signal.OTP in SafetyBarrier.analyzeMessage(report).evidence.map { it.id })
        assertTrue(Signal.OTP in SafetyBarrier.analyzeMessage("$warning Instead $demand").evidence.map { it.id })
    }

    @Test fun compareActualDetectors() = runBlocking {
        val root = listOf(File("."), File("app")).first { File(it,"src/main/assets/security").isDirectory }
        val dir = File(root,"src/main/assets/security")
        val adapter = LexicalMessageModel.load(File(dir,"message-v2.tsv").readBytes(), File(dir,"intent-v2.tsv").readBytes())
        checkNotNull(adapter.model)
        val lines = requireNotNull(javaClass.getResourceAsStream("/safety/directive17.tsv")).bufferedReader().use { it.readLines() }
        val output = File(root,"build/reports/security/paired-detectors.tsv")
        output.parentFile.mkdirs()
        output.bufferedWriter().use { out ->
            out.appendLine("id\tcategory\tlanguage\tlabel\trule_risk\tml_flag\tmargin\tcues\tevidence\tagreement")
            for (line in lines.drop(1)) {
                val (id, category, language, label, input) = line.split('\t', limit=5)
                if (category == "url") continue
                val result = adapter.analyze(input)
                val score = requireNotNull(result.shadow)
                val high = result.rule.risk == Risk.HIGH
                val agreement = when {
                    high && score.flagged -> "both"
                    high -> "rule_only"
                    score.flagged -> "model_only"
                    else -> "neither"
                }
                out.appendLine(listOf(id, category, language, label, result.rule.risk, score.flagged, score.margin,
                    score.observedCueTypes.sorted().joinToString(","), result.rule.evidence.map { it.id.name }.distinct().joinToString(","), agreement).joinToString("\t"))
            }
        }
        assertEquals(109, output.readLines().size)
    }
}
