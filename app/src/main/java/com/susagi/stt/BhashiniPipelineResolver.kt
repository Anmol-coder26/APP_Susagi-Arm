package com.susagi.stt

import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResolvedPipelineConfig(
    val serviceId: String,
    val callbackUrl: String,
    val inferenceApiKey: String,
    val isDirectRest: Boolean = false
)

/**
 * BhashiniPipelineResolver
 *
 * Resolves Bhashini pipeline endpoints using either:
 * 1. The official MeitY ULCA dynamic model pipeline resolution endpoint.
 * 2. Fallback to direct Dhruva REST / Streaming endpoints when ULCA returns an error or direct keys are present.
 */
class BhashiniPipelineResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val ULCA_CONFIG_URL = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline"
        private const val DEFAULT_DHRUVA_REST_URL = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun resolve(
        language: String,
        configuredUserId: String = BuildConfig.BHASHINI_USER_ID,
        configuredUlcaKey: String = BuildConfig.BHASHINI_ULCA_API_KEY,
        configuredInferenceKey: String = BuildConfig.BHASHINI_INFERENCE_API_KEY,
        configuredPipelineId: String = BuildConfig.BHASHINI_PIPELINE_ID.ifBlank { "64392f96daac500b55c543cd" },
        configuredEndpointUrl: String = BuildConfig.BHASHINI_STT_ENDPOINT
    ): ResolvedPipelineConfig {
        // If an explicit streaming or REST endpoint and direct inference key are already configured
        val directKey = configuredInferenceKey.ifBlank { configuredUlcaKey }
        val streamingUrl = BuildConfig.BHASHINI_STREAMING_URL

        if (streamingUrl.isNotBlank() && directKey.isNotBlank()) {
            CallDiagnostics.log("STT-RESOLV", "Using explicit streaming URL: $streamingUrl")
            return ResolvedPipelineConfig(
                serviceId = "bhashini/asr",
                callbackUrl = streamingUrl,
                inferenceApiKey = directKey,
                isDirectRest = false
            )
        }

        // Try ULCA dynamic handshake if credentials appear well-formed
        if (configuredUserId.isNotBlank() && configuredUlcaKey.isNotBlank() && !configuredUserId.contains("@")) {
            try {
                CallDiagnostics.log("STT-RESOLV", "Querying ULCA pipeline configuration at $ULCA_CONFIG_URL...")
                val payload = JSONObject().apply {
                    put("pipelineTasks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("taskType", "asr")
                            put("config", JSONObject().apply {
                                put("language", JSONObject().apply {
                                    put("sourceLanguage", language)
                                })
                            })
                        })
                    })
                    put("pipelineRequestConfig", JSONObject().apply {
                        put("pipelineId", configuredPipelineId)
                    })
                }

                val req = Request.Builder()
                    .url(ULCA_CONFIG_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("userID", configuredUserId)
                    .addHeader("ulcaApiKey", configuredUlcaKey)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val json = JSONObject(body)
                        val socketEp = json.optJSONObject("pipelineInferenceSocketEndPoint")
                        val apiEp = json.optJSONObject("pipelineInferenceAPIEndPoint")
                        val ep = socketEp ?: apiEp
                        val cbUrl = ep?.optString("callbackUrl", "")
                            ?: ep?.optString("socketUrl", "")
                            ?: ""
                        val keyObj = ep?.optJSONObject("inferenceApiKey")
                        val apiKey = keyObj?.optString("value", directKey) ?: directKey

                        if (cbUrl.isNotBlank()) {
                            CallDiagnostics.log("STT-RESOLV", "ULCA Handshake Success! Endpoint: $cbUrl")
                            return ResolvedPipelineConfig(
                                serviceId = "bhashini/asr",
                                callbackUrl = cbUrl,
                                inferenceApiKey = apiKey,
                                isDirectRest = cbUrl.contains("inference/pipeline")
                            )
                        }
                    } else {
                        CallDiagnostics.log("STT-RESOLV", "ULCA Handshake HTTP ${resp.code}: $body", LogLevel.WARN)
                    }
                }
            } catch (e: Exception) {
                CallDiagnostics.log("STT-RESOLV", "ULCA Handshake network error: ${e.message}", LogLevel.WARN)
            }
        }

        // Fallback: Use direct Bhashini Dhruva REST Pipeline endpoint
        val effectiveEndpoint = configuredEndpointUrl.ifBlank { DEFAULT_DHRUVA_REST_URL }
        CallDiagnostics.log("STT-RESOLV", "Resolved to Direct Dhruva Pipeline: $effectiveEndpoint")
        return ResolvedPipelineConfig(
            serviceId = "ai4bharat/conformer-$language",
            callbackUrl = effectiveEndpoint,
            inferenceApiKey = directKey,
            isDirectRest = true
        )
    }
}
