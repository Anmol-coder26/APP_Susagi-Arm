package com.susagi.safety

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OfflineEvaluationTest {
    private data class Observation(val category: String, val language: String, val positive: Boolean,
        val result: Assessment, val elapsed: Long)

    @Test fun evaluateFixtures() = runBlocking {
        val output = File(System.getProperty("susagi.eval.dir") ?: "build/reports/safety-eval")
        check(output.mkdirs() || output.isDirectory)
        val rows = mutableListOf<Observation>()
        val ids = mutableSetOf<String>()
        val inputs = mutableSetOf<String>()
        val source = requireNotNull(javaClass.getResourceAsStream("/safety/directive17.tsv"))
        output.resolve("predictions.tsv").bufferedWriter().use { writer ->
            writer.appendLine("id\tcategory\tlanguage\tlabel\tprediction\tsignals\taction\tns\terror")
            source.bufferedReader().useLines { lines ->
                for (line in lines.drop(1)) {
                    val (id, category, language, label, text) = line.split('\t', limit = 5)
                    require(ids.add(id) && inputs.add(text) && label in setOf("harmful", "benign"))
                    val start = System.nanoTime()
                    val result = if (category == "url") SafetyBarrier.analyzeUrl(text) else SafetyBarrier.analyzeMessage(text)
                    val row = Observation(category, language, label == "harmful", result, System.nanoTime() - start)
                    rows.add(row)
                    val error = when {
                        row.positive && result.risk != Risk.HIGH -> "missed_high"
                        !row.positive && result.risk == Risk.HIGH -> "false_alarm"
                        else -> ""
                    }
                    writer.appendLine(listOf(id, category, language, label, result.risk,
                        result.evidence.joinToString(",") { it.id.name }, result.action, row.elapsed, error).joinToString("\t"))
                }
            }
        }
        require(rows.isNotEmpty())
        output.resolve("metrics.tsv").bufferedWriter().use { writer ->
            writer.appendLine("group\tn\ttp\tfp\ttn\tfn\treview\tunknown\tprecision\trecall\tf1\tfpr\tfnr\tp95_ns\tmax_ns")
            val groups = mapOf("all" to rows) + rows.groupBy { "category/" + it.category } + rows.groupBy { "language/" + it.language }
            for ((group, items) in groups) {
                val tp = items.count { it.positive && it.result.risk == Risk.HIGH }
                val fp = items.count { !it.positive && it.result.risk == Risk.HIGH }
                val fn = items.count { it.positive } - tp
                val tn = items.size - tp - fp - fn
                fun ratio(a: Int, b: Int) = if (b == 0) "NA" else (a.toDouble() / b).toString()
                val times = items.map { it.elapsed }.sorted()
                writer.appendLine(listOf(group, items.size, tp, fp, tn, fn, items.count { it.result.risk == Risk.REVIEW },
                    items.count { it.result.risk == Risk.UNKNOWN }, ratio(tp,tp+fp), ratio(tp,tp+fn), ratio(2*tp,2*tp+fp+fn),
                    ratio(fp,fp+tn), ratio(fn,tp+fn), times[(times.size*95+99)/100-1], times.last()).joinToString("\t"))
            }
        }
    }
}
