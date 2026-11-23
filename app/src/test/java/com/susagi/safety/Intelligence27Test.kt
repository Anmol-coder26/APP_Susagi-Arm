package com.susagi.safety

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Intelligence27Test {
    @Test fun groundedConditions() = runBlocking {
        val demands = listOf(
            "Your salary remains withheld until you remit the clearance charge.",
            "An activation deposit is required before you can start.",
            "Read the digits from your bank text out loud."
        )
        for (message in demands) assertEquals(message, Risk.HIGH, SafetyBarrier.analyzeMessage(message).risk)
        for (message in listOf("Salary is on hold while payroll checks the timesheet.",
                "Never remit the clearance charge even if your salary is withheld.",
                "Never read the digits from your bank text out loud.")) {
            assertFalse(message, SafetyBarrier.analyzeMessage(message).risk == Risk.HIGH)
        }
    }

    @Test fun extractedObservationsPreserveClause() {
        val result = FraudIntent.inspect("Customer support: never pay the processing fee; instead scan this QR.")
        assertEquals(FraudIntent.Voice.WARNING, result.clauses.first().voice)
        assertTrue(FraudIntent.Role.SUPPORT in result.clauses.first().roles)
        assertEquals(setOf(FraudIntent.Act.SCAN), result.clauses.last().actions)
        assertTrue(result.clauses.first().index < result.clauses.last().index)
        assertTrue(FraudIntent.inspect("a".repeat(32769)).exceededLimit)
    }

    @Test fun encodedRedirect() = runBlocking {
        val nested = SafetyBarrier.analyzeUrl("https://site.example/?next=https%253A%252F%252Ffiles.example%252Fupdate.apk")
        assertTrue(nested.evidence.any { it.id == Signal.APK && it.host == "files.example" })
        val ordinary = SafetyBarrier.analyzeUrl("https://site.example/?next=%2Fdocs%2Ftea%2520room")
        assertFalse(ordinary.evidence.any { it.id == Signal.APK })
    }
}
