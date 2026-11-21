package com.susagi.edgeai

/**
 * Builds standard Llama 3.2 Instruct formatted prompts for cybersecurity threat analysis.
 */
object ThreatPromptBuilder {

    private const val SYSTEM_PROMPT = """You are SuSagi Guardian's On-Device Cybersecurity Intelligence Engine powered by Llama 3.2 and ExecuTorch on Arm hardware.
Analyze the target item and structured evidence for cyber threat indicators (phishing, social engineering, psychological coercion, digital arrest, or financial fraud).
Respond with a clear explanation and 2-3 specific, actionable remediation steps for the user.
Keep explanations concise, objective, and plain-language."""

    fun buildLlamaPrompt(input: ThreatInput): String {
        val userContent = buildString {
            appendLine("Channel: ${input.channel.name}")
            appendLine("Target: ${input.rawContent.trim()}")
            if (input.structuredEvidence.isNotEmpty()) {
                appendLine("Detected Evidence:")
                input.structuredEvidence.forEach { ev ->
                    appendLine("- $ev")
                }
            }
            if (input.metadata.isNotEmpty()) {
                appendLine("Context Metadata:")
                input.metadata.forEach { (k, v) ->
                    appendLine("- $k: $v")
                }
            }
            appendLine("\nProvide your threat assessment, brief explanation, and immediate recommended actions.")
        }

        // Llama 3.2 Instruct Chat Template
        return buildString {
            append("<|begin_of_text|>")
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append(SYSTEM_PROMPT)
            append("<|eot_id|>")
            append("<|start_header_id|>user<|end_header_id|>\n\n")
            append(userContent)
            append("<|eot_id|>")
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
}
