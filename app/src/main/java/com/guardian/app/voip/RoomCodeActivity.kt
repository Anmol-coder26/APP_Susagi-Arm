package com.guardian.app.voip

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimarySoft
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
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning

/**
 * Derives a shared Agora channel name from a 6-digit room code.
 * Both devices must produce the same channel string.
 */
object RoomCode {
    private const val PREFIX = "gx_"

    fun generate(): String = (100000..999999).random().toString()

    fun isValid(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }

    fun toChannel(code: String): String = PREFIX + code

    /** Recovers the display code from a channel name, for UI and call history. */
    fun fromChannel(channel: String): String = channel.removePrefix(PREFIX)
}

class RoomCodeActivity : ComponentActivity() {

    private var pendingCode: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            pendingCode?.let { launchCall(it) }
        } else {
            Toast.makeText(this, "Microphone permission is required to start a call", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GuardianTheme {
                RoomCodeScreen(
                    onJoin = { code -> startCall(code) },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun startCall(code: String) {
        if (!RoomCode.isValid(code)) {
            Toast.makeText(this, "Enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            launchCall(code)
        } else {
            pendingCode = code
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            permissionLauncher.launch(perms)
        }
    }

    private fun launchCall(code: String) {
        startActivity(
            Intent(this, VoipCallActivity::class.java)
                .putExtra("channel_name", RoomCode.toChannel(code))
                .putExtra("room_code", code)
        )
    }
}

@Composable
private fun RoomCodeScreen(
    onJoin: (String) -> Unit,
    onBack: () -> Unit
) {
    val myCode = remember { RoomCode.generate() }
    var enteredCode by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // TOP BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GxTextHi,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Secure VoIP Call",
                        color = GxTextHi,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "E2EE AUDIO · REAL-TIME AI DEFENSE",
                        color = GxPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // ==========================================
            // 1. HOST A CALL (GENERATE ROOM)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = GxPrimarySoft,
                                shape = CircleShape,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PhoneInTalk,
                                        contentDescription = null,
                                        tint = GxPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "HOST A SECURE CALL",
                                color = GxTextLo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Surface(
                            color = GxSafeSoft,
                            shape = GxShapePill,
                            border = BorderStroke(0.5.dp, GxSafe)
                        ) {
                            Text(
                                "READY",
                                color = GxSafe,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Segmented PIN Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val chunk1 = myCode.take(3)
                        val chunk2 = myCode.takeLast(3)

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chunk1.forEach { digit -> PinDigitBox(digit.toString()) }
                        }

                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(2.dp)
                                .background(GxTextLo)
                        )
                        Spacer(Modifier.width(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            chunk2.forEach { digit -> PinDigitBox(digit.toString()) }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Share this 6-digit room code with the person you want to call",
                        color = GxTextLo,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { onJoin(myCode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = GxShapeSm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GxPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("START CALL AS HOST", fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Guardian Room Code", myCode))
                                Toast.makeText(context, "Room code copied: $myCode", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxBorder)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GxTextMid, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Code", color = GxTextHi, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Join my secure Guardian call using Room Code: $myCode")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Room Code"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxBorder)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GxTextMid, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Code", color = GxTextHi, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Divider OR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = GxBorder)
                Text(
                    " OR ",
                    color = GxTextLo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = GxBorder)
            }

            Spacer(Modifier.height(20.dp))

            // ==========================================
            // 2. JOIN A CALL (ENTER CODE)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = GxSurfaceAlt,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = GxTextMid,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "JOIN EXISTING CALL",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    OutlinedTextField(
                        value = enteredCode,
                        onValueChange = { input ->
                            enteredCode = input.filter { it.isDigit() }.take(6)
                        },
                        singleLine = true,
                        placeholder = { Text("Enter 6-digit room code", color = GxTextLo) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GxTextHi,
                            unfocusedTextColor = GxTextHi,
                            focusedBorderColor = GxPrimary,
                            unfocusedBorderColor = GxBorder,
                            focusedContainerColor = GxSurfaceAlt,
                            unfocusedContainerColor = GxSurfaceAlt
                        ),
                        shape = GxShapeSm,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val isValid = RoomCode.isValid(enteredCode)

                    Button(
                        onClick = { onJoin(enteredCode) },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = GxShapeSm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isValid) GxSafe else GxSurfaceAlt,
                            contentColor = if (isValid) Color.White else GxTextLo,
                            disabledContainerColor = GxSurfaceAlt,
                            disabledContentColor = GxTextLo
                        )
                    ) {
                        Text(
                            "JOIN SECURE ROOM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ==========================================
            // TRUST & SECURITY FEATURES BADGE
            // ==========================================
            Surface(
                color = GxSurfaceAlt.copy(alpha = 0.5f),
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureCheckRow("Agora 16 kHz High-Fidelity Audio Frame Capture")
                    FeatureCheckRow("Dual-Channel Speech Recognition Pipeline")
                    FeatureCheckRow("Real-Time Threat Classifier & Coercion Radar")
                }
            }
        }
    }
}

@Composable
private fun PinDigitBox(digit: String) {
    Surface(
        color = Color(0xFFECFDF5),
        shape = GxShapeSm,
        border = BorderStroke(1.5.dp, Color(0xFF10B981)),
        modifier = Modifier.size(42.dp, 52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                color = Color(0xFF065F46),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FeatureCheckRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = GxSafe,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = GxTextMid, fontSize = 11.sp)
    }
}
