package com.susagi.safety

import java.io.File
import org.junit.Test
import org.junit.Assert.*

class LexicalParityTest {
    @Test fun integrityAndGoldenScores() {
        val dir=listOf("src/main/assets/security","app/src/main/assets/security").map(::File).first { it.isDirectory }
        val w=File(dir,"message-v2.tsv").readBytes()
        val s=File(dir,"intent-v2.tsv").readBytes()
        assertEquals("UNAVAILABLE",LexicalMessageModel.load(null,s).status)
        assertEquals("INVALID",LexicalMessageModel.load(w,byteArrayOf(1)).status)
        val model=requireNotNull(LexicalMessageModel.load(w,s).model)
        requireNotNull(javaClass.getResourceAsStream("/safety/lexical-parity.tsv")).bufferedReader().useLines { rows ->
            rows.drop(1).forEach { row ->
                val(hex,margin)=row.split('\t')
                val text=hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(Charsets.UTF_8)
                assertEquals(margin.toDouble(),model.score(text).margin,1e-9)
            }
        }
    }
}
