package com.susagi.safety

import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LexicalMessageModel private constructor(private val weights: Map<String,Double>, private val cues: Map<String,Set<String>>,
    private val bias: Double, val threshold: Double) {
    data class Score(val margin: Double,val flagged: Boolean,val observedCueTypes: Set<String>)
    data class Comparison(val rule: Assessment,val shadow: Score?,val modelStatus: String)
    data class Adapter internal constructor(val model: LexicalMessageModel?,val status: String) {
        suspend fun analyze(text: String): Comparison = withContext(Dispatchers.Default) {
            Comparison(SafetyBarrier.analyzeMessage(text),model?.score(text),status)
        }
    }
    fun score(text: String): Score {
        var end=0
        var n=0
        while(end<text.length && n++<2048) end+=Character.charCount(text.codePointAt(end))
        val normalized=buildString { for(i in 0 until end) append(if(text[i] in 'A'..'Z') text[i]+32 else text[i]) }
        val tokens=token.findAll(normalized).take(256).map { it.value }.toList()
        val keys=tokens.toMutableSet()
        tokens.zipWithNext { a,b -> keys.add(a+"_"+b) }
        val flags=cues.filterValues { terms -> terms.any { it in keys } }.keys.toSortedSet()
        for(a in flags) {
            keys.add("@"+a)
            for(b in flags) if(a<b) keys.add("@"+a+"+"+b)
        }
        var margin=bias
        for(k in keys.sorted()) margin+=weights[k] ?: 0.0
        return Score(margin,margin>=threshold,flags.toSet())
    }
    companion object {
        const val VERSION="lexical-v2"
        private val token=Regex("[a-z0-9\\u0900-\\u097f]+")
        private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun load(asset: ByteArray?,spec: ByteArray?): Adapter {
            if(asset==null || spec==null) return Adapter(null,"UNAVAILABLE")
            val cleanAsset = if (asset.contains('\r'.code.toByte())) asset.filter { it != '\r'.code.toByte() }.toByteArray() else asset
            val cleanSpec = if (spec.contains('\r'.code.toByte())) spec.filter { it != '\r'.code.toByte() }.toByteArray() else spec
            if(cleanAsset.size !in 1..131072 || cleanSpec.size !in 1..8192 ||
                digest(cleanAsset)!="f1c0f358126fd632671b7778159f7776f238c1e4cac3f6ab4e6eedbd23d62e78" ||
                digest(cleanSpec)!="55f4eaf0f2d23dd6d990d743ced1383fab0d80dc9fa313c1b8c0bef63fb23adb") return Adapter(null,"INVALID")
            val lines=cleanAsset.toString(Charsets.UTF_8).trimEnd().lines()
            require(lines.first()==VERSION)
            val weights=lines.drop(3).associate { it.substringBefore('\t') to it.substringAfter('\t').toDouble() }
            val cues=cleanSpec.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimEnd().lines().associate {
                it.substringBefore('\t') to it.substringAfter('\t').split(' ').toSet()
            }
            return Adapter(LexicalMessageModel(weights,cues,lines[1].toDouble(),lines[2].toDouble()),"LOADED")
        }
    }
}
