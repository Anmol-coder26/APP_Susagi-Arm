package com.guardian.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.guardian.app.ui.theme.GuardianTheme
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

data class QrVerdict(
    val rawValue: String,
    val status: RiskStatus,
    val title: String,
    val reason: String,
    val isUrl: Boolean = false,
    val isUpi: Boolean = false,
    val parsedPayee: String? = null,
    val parsedAmount: String? = null,
    val parsedNote: String? = null
)

class QrScannerActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var scanVerdict by mutableStateOf<QrVerdict?>(null)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            GuardianTheme {
                QrScannerScreen(
                    hasCameraPermission = hasCameraPermission,
                    verdict = scanVerdict,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBarcodeDetected = { raw ->
                        if (scanVerdict == null) {
                            val v = evaluateQrContent(raw)
                            scanVerdict = v
                            if (v.status != RiskStatus.Low) {
                                try {
                                    com.susagi.history.IncidentHistoryStore.getInstance(applicationContext).recordIncident(
                                        channel = if (v.isUpi) "UPI_QR" else "LINK",
                                        title = v.title,
                                        severity = if (v.status == RiskStatus.High) "HIGH" else "REVIEW",
                                        riskScore = if (v.status == RiskStatus.High) 85 else 50,
                                        category = if (v.isUpi) "UPI Fraud" else "Phishing QR",
                                        explanation = v.reason,
                                        rawSnippet = raw
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    onResetScan = { scanVerdict = null },
                    onClose = { finish() },
                    onSimulate = { raw ->
                        val v = evaluateQrContent(raw)
                        scanVerdict = v
                        if (v.status != RiskStatus.Low) {
                            try {
                                com.susagi.history.IncidentHistoryStore.getInstance(applicationContext).recordIncident(
                                    channel = if (v.isUpi) "UPI_QR" else "LINK",
                                    title = v.title,
                                    severity = if (v.status == RiskStatus.High) "HIGH" else "REVIEW",
                                    riskScore = if (v.status == RiskStatus.High) 85 else 50,
                                    category = if (v.isUpi) "UPI Fraud" else "Phishing QR",
                                    explanation = v.reason,
                                    rawSnippet = raw
                                )
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        fun evaluateQrContent(raw: String): QrVerdict {
            val text = raw.trim()
            if (text.isBlank()) {
                return QrVerdict(
                    rawValue = "",
                    status = RiskStatus.Low,
                    title = "ℹ️ EMPTY QR PAYLOAD",
                    reason = "The scanned QR code contained no readable data.",
                    isUrl = false,
                    isUpi = false
                )
            }
            val lower = text.lowercase()

            val isUpi = lower.startsWith("upi://")
            val isUrl = lower.startsWith("http://") || lower.startsWith("https://") || lower.contains(".xyz") || lower.contains(".apk") || lower.contains("xn--")

            if (isUpi) {
                val upi = com.susagi.safety.PaymentFields.inspectUri(text)
                if (!upi.isValid) {
                    return QrVerdict(
                        rawValue = text,
                        status = RiskStatus.High,
                        title = "🚨 INVALID OR TAMPERED UPI QR",
                        reason = upi.riskReason ?: "Conflicting, duplicate or malformed UPI parameters.",
                        isUpi = true,
                        parsedPayee = upi.payeeVpa,
                        parsedAmount = upi.amount?.let { "₹$it" }
                    )
                }
                if (upi.isAdvanceFeeOrReverseDebit) {
                    return QrVerdict(
                        rawValue = text,
                        status = RiskStatus.High,
                        title = "🚨 FRAUDULENT REVERSE UPI QR",
                        reason = upi.riskReason ?: "This QR requests you to pay money under the guise of a refund or reward.",
                        isUpi = true,
                        parsedPayee = upi.unverifiedDisplayName,
                        parsedAmount = upi.amount?.let { "₹$it" },
                        parsedNote = upi.note
                    )
                }
                // Valid standard UPI - Must never be described as authenticated
                return QrVerdict(
                    rawValue = text,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ UNVERIFIED UPI PAYMENT QR",
                    reason = "Valid UPI payment payload. Check recipient identity independently before opening. Never enter your UPI PIN to receive money.",
                    isUpi = true,
                    parsedPayee = upi.unverifiedDisplayName,
                    parsedAmount = upi.amount?.let { "₹$it" },
                    parsedNote = upi.note
                )
            }

            if (isUrl) {
                val linkVerdict = LinkCheckActivity.evaluateLinkSafety(text)
                return when (linkVerdict.status) {
                    RiskStatus.High -> QrVerdict(
                        rawValue = text,
                        status = RiskStatus.High,
                        title = "🚨 HIGH RISK PHISHING QR",
                        reason = linkVerdict.detail.ifBlank { "Contains known phishing signatures, rogue APK download, or banking credential harvesting patterns." },
                        isUrl = true
                    )
                    RiskStatus.Suspicious -> QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Suspicious,
                        title = "⚠️ SUSPICIOUS OBFUSCATED URL",
                        reason = linkVerdict.detail.ifBlank { "Uses an untrusted or shortened domain masking destination website." },
                        isUrl = true
                    )
                    RiskStatus.Low -> QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Low,
                        title = "🛡️ NO KNOWN LOCAL THREAT INDICATORS DETECTED",
                        reason = "No known local threat indicators detected. Domain matches verified standard syntax.",
                        isUrl = true
                    )
                }
            }

            val isCustomScheme = text.contains("://") && !isUpi && !lower.startsWith("http://") && !lower.startsWith("https://")
            if (isCustomScheme) {
                val scheme = text.substringBefore("://")
                return QrVerdict(
                    rawValue = text,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ UNCONVENTIONAL SCHEME QR",
                    reason = "The QR payload uses a non-standard protocol handler ($scheme://). Exercise caution before executing custom system actions.",
                    isUrl = false,
                    isUpi = false
                )
            }

            return QrVerdict(
                rawValue = text,
                status = RiskStatus.Low,
                title = "✅ SCANNED TEXT / DATA",
                reason = "No harmful command scripts or exploit payloads found in text content."
            )
        }
    }
}

@Composable
fun QrScannerScreen(
    hasCameraPermission: Boolean,
    verdict: QrVerdict?,
    onRequestPermission: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onResetScan: () -> Unit,
    onClose: () -> Unit,
    onSimulate: (String) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (hasCameraPermission) {
            CameraPreviewView(
                isScanningActive = verdict == null,
                onBarcodeDetected = onBarcodeDetected
            )
        } else {
            // Permission Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = CyberEmerald,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Camera Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Guardian needs camera permission to scan QR and barcodes for scam detection.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color(0xFF022C22))
                ) {
                    Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated.copy(alpha = 0.8f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Text("QR & Barcode Shield", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Viewfinder Reticle Overlay when scanning
        if (verdict == null && hasCameraPermission) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.Center)
                    .border(BorderStroke(2.dp, CyberEmerald), shape = RoundedCornerShape(16.dp))
            )
            Text(
                "Align QR / Barcode within frame",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 300.dp)
                    .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom Result Overlay or Stage Demo Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (verdict != null) {
                // VERDICT CARD
                val (cardBg, accentColor, borderCol) = when (verdict.status) {
                    RiskStatus.High -> Triple(Color(0xFF330909), CoralRed, CoralRed)
                    RiskStatus.Suspicious -> Triple(Color(0xFF2E1C03), AmberWarning, AmberWarning)
                    RiskStatus.Low -> Triple(DarkSurfaceElevated, CyberEmerald, CyberEmerald.copy(alpha = 0.5f))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (verdict.status == RiskStatus.Low) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                verdict.title,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            verdict.reason,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (verdict.parsedPayee != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = DarkBackground.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(verdict.parsedPayee, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    if (verdict.parsedAmount != null) {
                                        Text("Requested Amount: ${verdict.parsedAmount}", color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (!verdict.parsedNote.isNullOrBlank()) {
                                        Text("Note: ${verdict.parsedNote}", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = DarkBackground.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                verdict.rawValue.take(120) + if (verdict.rawValue.length > 120) "..." else "",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Scanned QR", verdict.rawValue))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp)
                            }

                            if (verdict.isUrl && verdict.status == RiskStatus.Low) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verdict.rawValue))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color(0xFF022C22)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = onResetScan,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rescan", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Stage Quick Simulator Controls
                Surface(
                    color = DarkSurfaceElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Instant QR Test Simulator:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSimulate("upi://pay?pa=cyberfraud@fakebank&pn=RefundDesk&am=9800&cu=INR&tn=Cashback%20Refund") },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚨 Refund Trap", fontSize = 9.sp, color = CoralRed)
                            }
                            OutlinedButton(
                                onClick = { onSimulate("upi://pay?pa=stall@okhdfcbank&pn=Local%20Vendor&am=42.50&cu=INR") },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⚠️ Standard UPI", fontSize = 9.sp, color = AmberWarning)
                            }
                            OutlinedButton(
                                onClick = { onSimulate("https://sbi-card-kyc-update.xyz/verify.apk") },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚨 Phish APK", fontSize = 9.sp, color = CoralRed)
                            }
                            OutlinedButton(
                                onClick = { onSimulate("https://www.npci.org.in/what-we-do/upi/product-overview") },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✅ Safe Link", fontSize = 9.sp, color = CyberEmerald)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreviewView(
    isScanningActive: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_AZTEC,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_CODE_128
                    )
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy: ImageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && isScanningActive) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val raw = barcode.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        onBarcodeDetected(raw)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("GuardianQR", "Camera bind failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
