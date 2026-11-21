package com.guardian.app.voip

import android.content.Context
import android.util.Log
import com.guardian.app.RoleAnalysisResponse
import com.guardian.app.SemanticAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FIXES: Unstable or inconsistent role/scam parsing from LLM outputs.
 * Enforces JSON Schema parsing for victim/scammer role classification and risk scores.
 */
class GeminiRoleClient(
    private val context: Context? = null,
    private val analyzer: SemanticAnalyzer = SemanticAnalyzer(context)
) {
    suspend fun analyzeConversation(
        transcriptBuffer: String,
        language: String = "hi"
    ): RoleAnalysisResponse = withContext(Dispatchers.IO) {
        if (transcriptBuffer.isBlank()) {
            return@withContext RoleAnalysisResponse()
        }

        try {
            val response = analyzer.analyzeChunkWithRoles(transcriptBuffer, language)
            Log.d(
                "GeminiRoleClient",
                "Analyzed transcript: score=${response.riskScore}, victim=${response.victimRole}, scammer=${response.scammerRole}, confidence=${response.roleConfidence}"
            )
            response
        } catch (e: Exception) {
            Log.e("GeminiRoleClient", "Gemini analysis error: ${e.message}", e)
            RoleAnalysisResponse(
                riskScore = 0,
                victimRole = "Unknown",
                scammerRole = "Unknown",
                roleConfidence = 0f,
                roleReasoning = "Analysis currently unavailable."
            )
        }
    }
}
