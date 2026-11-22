package com.susagi.safety

import com.susagi.intelligence.UnifiedThreatEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RoundOneDetectorTest {
    @Test fun lockedMessageComparison() = runBlocking {
        val model = MessageModel.Adapter.load(listOf("src/main/assets","app/src/main/assets").map {
            File(it,"security/message-v1.txt") }.first { it.isFile }.readBytes())
        requireNotNull(javaClass.getResourceAsStream("/safety/model-parity.tsv")).bufferedReader().useLines { rows ->
            rows.drop(1).forEach { row ->
                val (hex, expected) = row.split('\t')
                val input = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
                assertEquals(expected.toDouble(),requireNotNull(model.score(input).margin),1e-8)
            }
        }
        val engine = UnifiedThreatEngine()
        val report = File("build/reports/round1/messages.tsv")
        report.parentFile?.mkdirs()
        report.bufferedWriter().use { writer ->
            writer.appendLine("id\tcategory\tlanguage\texpected\trule\tshadow_flag\tfinal\tunified_severity")
            val counts = IntArray(4)
            var undecided = 0
            requireNotNull(javaClass.getResourceAsStream("/safety/directive17.tsv")).bufferedReader().useLines { rows ->
                for (row in rows.drop(1)) {
                    val (id,category,lang,label,text) = row.split('\t',limit=5)
                    if (category !in setOf("authority","investment")) continue
                    val rule = SafetyBarrier.analyzeMessage(text)
                    val comparison = MessageModel.analyze(text,model)
                    assertEquals(rule.risk,comparison.verdict.risk)
                    assertEquals(rule.action,comparison.verdict.action)
                    assertEquals(MessageModel.Status.SCORED,comparison.shadow.status)
                    val high = rule.risk == Risk.HIGH
                    val harmful = label == "harmful"
                    counts[if (high && harmful) 0 else if (high) 1 else if (harmful) 3 else 2]++
                    if (rule.risk in setOf(Risk.REVIEW,Risk.UNKNOWN)) undecided++
                    writer.appendLine(listOf(id,category,lang,label,rule.risk,comparison.shadow.flagged,comparison.verdict.risk,
                        engine.analyzeMessage(text).severity).joinToString("\t"))
                }
            }
            assertEquals(24,counts.sum())
            assertEquals(12, counts[0]) // TP = 12 / 12 (100% Recall)
            assertEquals(0, counts[1])  // FP = 0 (0% FPR, 100% Precision)
            assertEquals(12, counts[2]) // TN = 12 / 12
            assertEquals(0, counts[3])  // FN = 0
            println("Rule and shadow-fused final TP/FP/TN/FN="+counts.joinToString("/")+" review_or_unknown="+undecided)
        }
    }
}
