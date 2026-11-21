package com.susagi.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxSafe
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
import kotlinx.coroutines.launch

@Composable
fun ThreatResultsScreen(
    threatTitle: String = "Digital Arrest / Coercion Pattern",
    threatCategory: String = "Impersonation Extortion",
    riskScore: Int = 85,
    signals: List<String> = listOf("CBI / Police Impersonation", "Urgent Transfer Demand", "Threat of Imprisonment"),
    explanation: String = "The caller claimed to represent federal law enforcement and demanded immediate financial clearance under threat of digital arrest. Legitimate police never issue arrest warrants over telephone or video calls.",
    onBack: () -> Unit = {},
    onSendEmergencyAlert: () -> Unit = {}
) {
    val context = LocalContext.current
    var alertSent by remember { mutableStateOf(false) }

    val accentColor = when {
        riskScore >= 70 -> GxDanger
        riskScore >= 35 -> GxWarning
        else -> GxSafe
    }
    val bgSoft = when {
        riskScore >= 70 -> GxDangerSoft
        riskScore >= 35 -> GxWarningSoft
        else -> Color(0xFFECFDF5)
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
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
                        text = "Threat Assessment",
                        color = GxTextHi,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "INCIDENT AUDIT LOG",
                        color = GxPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Hero Threat Meter
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeLg,
                colors = CardDefaults.cardColors(containerColor = bgSoft),
                border = BorderStroke(1.5.dp, accentColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = accentColor,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            color = accentColor,
                            shape = GxShapePill
                        ) {
                            Text(
                                text = "THREAT INDEX $riskScore/100",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = threatTitle,
                        color = GxTextHi,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Category: $threatCategory",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = explanation,
                        color = GxTextMid,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            // Detected Attack Signals
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DETECTED COERCION SIGNALS",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    signals.forEach { signal ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = GxDangerSoft,
                                shape = CircleShape,
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = signal,
                                color = GxTextHi,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Family Escalation Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = GxPrimarySoft,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = GxPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TRUSTED FAMILY ESCALATION",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Text(
                        text = "Instantly notify your linked emergency family contacts with this threat snapshot and audio timestamp.",
                        color = GxTextMid,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Button(
                        onClick = {
                            alertSent = true
                            onSendEmergencyAlert()
                            Toast.makeText(context, "Emergency alert dispatched to trusted contacts", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !alertSent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = GxShapeSm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alertSent) GxSafe else GxDanger,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = if (alertSent) "ALERT SENT TO FAMILY" else "DISPATCH FAMILY ALERT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
