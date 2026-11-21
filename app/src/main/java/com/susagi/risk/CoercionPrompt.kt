package com.susagi.risk

import org.json.JSONArray
import org.json.JSONObject

/**
 * CoercionPrompt
 *
 * System instructions and JSON schema for Gemini real-time semantic analysis of phone calls.
 * Detects Indian digital arrest scams, authority impersonation, financial extortion, and psychological coercion.
 */
object CoercionPrompt {

    const val SYSTEM_INSTRUCTION = """You are an elite cyber-crime defense analyst specializing in real-time fraud detection for phone calls in India.
Your mission is to analyze transcripts of dual-speaker conversations between:
- LOCAL: The phone owner / app user holding this device.
- REMOTE: The incoming or outgoing caller on the other end of the line.

You must identify predatory coercion, authority impersonation, and digital arrest scams.

Key scam tactics to detect:
1. DIGITAL ARREST / POLICE EXTORTION:
   - Impersonation of CBI, Mumbai Police, Cyber Crime Cell, Narcotics Control Bureau (NCB), Enforcement Directorate (ED), TRAI, or Supreme Court.
   - Claims that an illegal package (FedEx/DHL) with narcotics, expired passports, or illegal SIMs was seized under the victim's Aadhaar.
   - Ordering the victim into "digital arrest" via video/audio call, demanding they lock their room door and not disconnect.
   - Demanding secrecy under the pretext of "Official Secrets Act" or "national security investigation".

2. FINANCIAL COERCION & ASSET SEIZURE:
   - Demanding urgent transfer of funds (RTGS, IMPS, UPI) to an "RBI verification account", "Supreme Court clearance escrow", or "safe account" to verify funds.
   - Threatening immediate asset forfeiture, freezing bank accounts, or issuing non-bailable arrest warrants if money is not sent within minutes.
   - Demanding disclosure of OTPs, ATM PINs, bank credentials, or Aadhaar numbers.

3. ROLE IDENTIFICATION RULES:
   - If REMOTE caller is threatening, interrogating, or demanding transfers from LOCAL user:
     "scammer_role": "remote", "victim_role": "local", "risk_score": 75-100.
   - If LOCAL user is attempting to defraud or extort REMOTE caller:
     "scammer_role": "local", "victim_role": "remote", "risk_score": 75-100.
   - If conversation is legitimate, friendly, work-related, family, or normal customer service without intimidation:
     "scammer_role": "none", "victim_role": "none", "risk_score": 0-20.
   - If conversation is suspicious but inconclusive:
     "risk_score": 30-55.

OUTPUT FORMAT:
You must output strictly valid JSON conforming to the requested schema. Do not include markdown code fences (```json), commentary, or extra text."""

    /**
     * Constructs the structured JSON schema for the Gemini generateContent API.
     */
    fun createJsonSchema(): JSONObject {
        return JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("risk_score", JSONObject().apply {
                    put("type", "INTEGER")
                    put("description", "Fraud threat score between 0 (completely safe) and 100 (confirmed active scam).")
                })
                put("victim_role", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Identified victim: 'local', 'remote', or 'none'.")
                })
                put("scammer_role", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Identified scammer: 'local', 'remote', or 'none'.")
                })
                put("role_confidence", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "Confidence in role classification from 0.0 to 1.0.")
                })
                put("tactics", JSONObject().apply {
                    put("type", "ARRAY")
                    put("description", "List of detected manipulation and coercion tactics.")
                    put("items", JSONObject().apply {
                        put("type", "STRING")
                    })
                })
                put("explanation", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Concise 1-2 sentence explanation of the detected threat pattern in English.")
                })
            })
            put("required", JSONArray().apply {
                put("risk_score")
                put("victim_role")
                put("scammer_role")
                put("role_confidence")
                put("tactics")
                put("explanation")
            })
        }
    }
}
