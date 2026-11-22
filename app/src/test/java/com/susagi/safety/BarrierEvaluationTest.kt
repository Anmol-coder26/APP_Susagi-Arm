package com.susagi.safety

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.text.Normalizer
import java.util.Locale

class BarrierEvaluationTest {
    @Test fun measureFrozenFixtures() = runBlocking {
        val rows = requireNotNull(javaClass.getResourceAsStream("/safety/barrier.tsv")).bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { it.split('\t', limit = 5) }.toList()
        }
        val families = mutableMapOf<String,String>()
        val fingerprints = mutableSetOf<String>()
        for ((split, family, _, _, input) in rows) {
            assertTrue(split in setOf("validation","test"))
            assertEquals(split,families.getOrPut(family) { split })
            val canonical = Normalizer.normalize(input,Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+")," ")
            assertTrue("Duplicate input",fingerprints.add(canonical))
        }
        val groups = rows.groupBy { it[0] }.toMutableMap()
        // Authored category pairs are also reported separately from the aggregate test split.
        groups.putAll(rows.filter { it[0] == "test" && it[1].startsWith("pair.") }.groupBy { it[1] })
        for ((split, cases) in groups) {
            var tp=0; var tn=0; var fp=0; var fn=0; var review=0
            val latencies = mutableListOf<Long>()
            for ((_,_,kind,label,input) in cases) {
                val start = System.nanoTime()
                val result = if (kind == "url") SafetyBarrier.analyzeUrl(input) else SafetyBarrier.analyzeMessage(input)
                latencies += System.nanoTime()-start
                if (result.risk == Risk.REVIEW || result.risk == Risk.UNKNOWN) review++
                val high = result.risk == Risk.HIGH
                if (label == "1") { if (high) tp++ else fn++ }
                else { if (high) fp++ else tn++ }
            }
            fun ratio(a:Int,b:Int) = if (b==0) Double.NaN else a.toDouble()/b
            latencies.sort()
            println("BARRIER $split TP=$tp FP=$fp TN=$tn FN=$fn review=$review precision="+ratio(tp,tp+fp)+
                " recall="+ratio(tp,tp+fn)+" F1="+ratio(2*tp,2*tp+fp+fn)+" FPR="+ratio(fp,fp+tn)+
                " FNR="+ratio(fn,fn+tp)+" p50_us="+latencies[(latencies.size-1)/2]/1000+
                " p95_us="+latencies[(latencies.size*95+99)/100-1]/1000)
            assertEquals(cases.size,tp+tn+fp+fn)
        }
    }
}
