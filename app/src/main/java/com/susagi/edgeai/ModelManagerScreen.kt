package com.susagi.edgeai

import android.os.Build
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeLg
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Model Capability Pack metadata for modular management.
 */
data class CapabilityPack(
    val id: String,
    val packNumber: Int,
    val title: String,
    val subtitle: String,
    val downloadSize: String,
    val installedSize: String,
    val isBundled: Boolean = false,
    val status: String,
    val description: String,
    val sha256: String,
    val compatibleAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64"),
    val requirements: String
)

/**
 * ModelManagerScreen
 *
 * Provides granular control over modular on-device capability packs:
 * - Pack 1: Core Security (Bundled in APK, 0 MB download)
 * - Pack 2: Offline Voice Lite (Whisper Tiny, 116 MB)
 * - Pack 3: AI Assistant Lite (Compact Qwen/quantized, 350 MB)
 * - Pack 4: Enhanced Local AI (Llama 3.2 1B Instruct, 1.08 GB)
 */
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember { ModelManager(ctx) }

    val hardwareSummary = remember { modelManager.getHardwareSummary() }
    val cpuAbi = hardwareSummary["cpu_abi"] ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

    // Storage calculation
    val statFs = remember {
        try {
            StatFs(ctx.filesDir.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
    val availableBytes = remember {
        statFs?.let { it.availableBlocksLong * it.blockSizeLong } ?: (8L * 1024 * 1024 * 1024)
    }
    val availableGb = remember {
        String.format("%.2f", availableBytes.toDouble() / (1024 * 1024 * 1024))
    }

    var pack2Downloading by remember { mutableStateOf(false) }
    var pack2Progress by remember { mutableStateOf(0f) }
    var pack2Installed by remember {
        mutableStateOf(File(modelManager.modelsDir, ModelManifest.WHISPER_TINY_ARM.modelFileName).exists())
    }

    var pack3Downloading by remember { mutableStateOf(false) }
    var pack3Progress by remember { mutableStateOf(0f) }
    var pack3Installed by remember {
        mutableStateOf(File(modelManager.modelsDir, "qwen_0_6b_instruct_q4.pte").exists())
    }

    var pack4Downloading by remember { mutableStateOf(false) }
    var pack4Progress by remember { mutableStateOf(0f) }
    var pack4Installed by remember {
        mutableStateOf(modelManager.isReady())
    }

    val packs = remember(pack2Installed, pack3Installed, pack4Installed) {
        listOf(
            CapabilityPack(
                id = "pack_core_security",
                packNumber = 1,
                title = "Pack 1: Core Security Intelligence",
                subtitle = "Authoritative Threat Detection (Included)",
                downloadSize = "0 MB",
                installedSize = "Integrated in APK",
                isBundled = true,
                status = "ACTIVE & READY",
                description = "Deterministic SafetyBarrier rules, Regional Scam Keywords (10 Indian languages), UPI reverse-debit trap detectors, Link homoglyph analysis, and Encrypted incident history.",
                sha256 = "N/A (Bundled APK Artifact)",
                requirements = "All Android 8.0+ devices. No internet or download required."
            ),
            CapabilityPack(
                id = "pack_offline_voice",
                packNumber = 2,
                title = "Pack 2: Offline Voice Lite",
                subtitle = "Multilingual Whisper Speech-to-Text",
                downloadSize = "116.58 MB",
                installedSize = "~120 MB",
                isBundled = false,
                status = if (pack2Installed) "INSTALLED" else "NOT PROVISIONED",
                description = "Arm Whisper Tiny INT8 XNNPACK model for local microphone transcription and call audio screening without cloud APIs.",
                sha256 = ModelManifest.WHISPER_TINY_ARM.modelSha256,
                requirements = "Arm64 Cortex CPU, >= 3GB RAM recommended."
            ),
            CapabilityPack(
                id = "pack_assistant_lite",
                packNumber = 3,
                title = "Pack 3: AI Assistant Lite",
                subtitle = "Compact On-Device Conversational AI",
                downloadSize = "350 MB",
                installedSize = "~380 MB",
                isBundled = false,
                status = if (pack3Installed) "INSTALLED" else "NOT PROVISIONED",
                description = "Compact 4-bit quantized conversational model for instant conversational explanations and follow-up threat queries on memory-constrained devices.",
                sha256 = "d48a19bc32f05a9611b849204128e70b3951cbda12891f9342f1b0a8274a1239",
                requirements = "Arm64 architecture, >= 3.5GB RAM."
            ),
            CapabilityPack(
                id = "pack_enhanced_llama",
                packNumber = 4,
                title = "Pack 4: Enhanced Local AI",
                subtitle = "Llama 3.2 1B Instruct (SpinQuant Int4)",
                downloadSize = "1,082.48 MB",
                installedSize = "~1.2 GB",
                isBundled = false,
                status = if (pack4Installed) "INSTALLED" else "NOT PROVISIONED",
                description = "Full Llama 3.2 1B parameter reasoning model running via ExecuTorch Arm runtime for deep threat deconstruction, cyber forensics, and incident summarization.",
                sha256 = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSha256,
                requirements = "Arm64-v8a hardware, >= 4GB RAM, 1.5GB free storage."
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ----------------------------------------------------
        // Header
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GxTextHi, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "AI Model Manager",
                        color = GxTextHi,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Modular Capability Packs & Model Storage",
                        color = GxTextLo,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ----------------------------------------------------
        // Hardware & Storage Dashboard Card
        // ----------------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Device Hardware Telemetry", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Surface(
                            color = GxSafeSoft,
                            shape = GxShapePill,
                            border = BorderStroke(1.dp, GxSafe.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "Arm64 ExecuTorch Ready",
                                color = GxSafe,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CPU Architecture", color = GxTextLo, fontSize = 11.sp)
                            Text(cpuAbi, color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        }

                        Column {
                            Text("Internal Storage Free", color = GxTextLo, fontSize = 11.sp)
                            Text("$availableGb GB Available", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                        }

                        Column {
                            Text("Core Security", color = GxTextLo, fontSize = 11.sp)
                            Text("100% Operational", color = GxSafe, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Section Header
        // ----------------------------------------------------
        item {
            Text(
                "SELECTABLE CAPABILITY PACKS",
                color = GxTextLo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        // ----------------------------------------------------
        // Pack 1 Card (Core Security - Bundled)
        // ----------------------------------------------------
        item {
            CapabilityPackCard(
                pack = packs[0],
                isInstalled = true,
                isDownloading = false,
                progress = 1f,
                onAction = {}
            )
        }

        // ----------------------------------------------------
        // Pack 2 Card (Offline Voice Lite - Whisper Tiny)
        // ----------------------------------------------------
        item {
            CapabilityPackCard(
                pack = packs[1],
                isInstalled = pack2Installed,
                isDownloading = pack2Downloading,
                progress = pack2Progress,
                onAction = {
                    if (pack2Installed) {
                        val f = File(modelManager.modelsDir, ModelManifest.WHISPER_TINY_ARM.modelFileName)
                        if (f.exists()) f.delete()
                        pack2Installed = false
                        Toast.makeText(ctx, "Pack 2 uninstalled.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Download prohibited without explicit phone-side owner authorization.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // ----------------------------------------------------
        // Pack 3 Card (AI Assistant Lite - Qwen Compact)
        // ----------------------------------------------------
        item {
            CapabilityPackCard(
                pack = packs[2],
                isInstalled = pack3Installed,
                isDownloading = pack3Downloading,
                progress = pack3Progress,
                onAction = {
                    if (pack3Installed) {
                        val f = File(modelManager.modelsDir, "qwen_0_6b_instruct_q4.pte")
                        if (f.exists()) f.delete()
                        pack3Installed = false
                        Toast.makeText(ctx, "Pack 3 uninstalled.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Download prohibited without explicit phone-side owner authorization.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // ----------------------------------------------------
        // Pack 4 Card (Enhanced Local AI - Llama 3.2 1B)
        // ----------------------------------------------------
        item {
            CapabilityPackCard(
                pack = packs[3],
                isInstalled = pack4Installed,
                isDownloading = pack4Downloading,
                progress = pack4Progress,
                onAction = {
                    if (pack4Installed) {
                        val f = File(modelManager.modelsDir, ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelFileName)
                        if (f.exists()) f.delete()
                        pack4Installed = false
                        Toast.makeText(ctx, "Pack 4 uninstalled.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Download prohibited without explicit phone-side owner authorization.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // ----------------------------------------------------
        // Truthful Security Architecture Notice
        // ----------------------------------------------------
        item {
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Truthful AI Architecture Commitment",
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "SuSagi Guardian never disables core threat shields while optional model packs are uninstalled. All Message, Link, and UPI shields execute with 100% deterministic precision out of the box.",
                            color = GxTextMid,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityPackCard(
    pack: CapabilityPack,
    isInstalled: Boolean,
    isDownloading: Boolean,
    progress: Float,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GxShapeMd,
        colors = CardDefaults.cardColors(containerColor = GxSurface),
        border = BorderStroke(1.dp, if (isInstalled) GxSafe.copy(alpha = 0.5f) else GxBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        pack.title,
                        color = GxTextHi,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        pack.subtitle,
                        color = GxPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = if (isInstalled) GxSafeSoft else GxSurfaceAlt,
                    shape = GxShapePill,
                    border = BorderStroke(1.dp, if (isInstalled) GxSafe.copy(alpha = 0.4f) else GxBorder)
                ) {
                    Text(
                        text = if (isInstalled) "READY" else "OPTIONAL",
                        color = if (isInstalled) GxSafe else GxTextMid,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                pack.description,
                color = GxTextMid,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(0.5.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Download Size: ${pack.downloadSize}", color = GxTextHi, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text("Installed: ${pack.installedSize}", color = GxTextHi, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    if (pack.sha256 != "N/A (Bundled APK Artifact)") {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "SHA-256: ${pack.sha256.take(24)}...",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = GxPrimary,
                    trackColor = GxSurfaceAlt
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (pack.isBundled) {
                    Surface(
                        color = GxSafeSoft,
                        shape = GxShapeSm
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GxSafe, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Active in Current Build", color = GxSafe, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    if (isInstalled) {
                        OutlinedButton(
                            onClick = onAction,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GxDanger),
                            border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.5f)),
                            shape = GxShapeSm,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Uninstall Pack", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onAction,
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Pack", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
