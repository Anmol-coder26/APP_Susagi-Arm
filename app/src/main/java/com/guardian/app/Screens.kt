package com.guardian.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guardian.app.ui.components.GxButton
import com.guardian.app.ui.components.GxCard
import com.guardian.app.ui.components.GxChip
import com.guardian.app.ui.components.GxChipVariant
import com.guardian.app.ui.components.GxLiveDot
import com.guardian.app.ui.components.GxRiskRing
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimaryGlow
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
import com.guardian.app.ui.theme.GxType
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import com.guardian.app.ui.theme.gxFancyGlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 1. AUTH SCREEN (Obsidian Security Aesthetic)
// ==========================================
@Composable
fun AuthScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // Glowing Logo Shield
            Surface(
                color = GxPrimarySoft,
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = GxPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Guardian Defense",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = GxTextHi
            )
            Text(
                "Next-generation AI security defending calls, phishing links, and extortive messages in 10+ Indic languages.",
                color = GxTextMid,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name", color = GxTextLo) },
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address", color = GxTextLo) },
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Security PIN / Password", color = GxTextLo) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = customGxTextFieldColors(),
                shape = GxShapeMd,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            GxButton.Primary(
                text = "Launch Security Hub",
                onClick = { onContinue(name.trim().ifBlank { "User" }) },
                enabled = email.isNotBlank() && password.length >= 4,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "On-device zero-telemetry sandbox. Encrypted session state.",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ==========================================
// 2. 5-STEP ONBOARDING PAGER (Premium Linear Flow)
// ==========================================
@Composable
fun OnboardingScreen(name: String, onComplete: () -> Unit) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    // Permission States
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasDefaultBrowser by remember {
        mutableStateOf(isDefaultBrowser(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasCallControlPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPhonePermission = granted }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    val callControlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCallControlPermission = granted }

    var selectedLang by remember {
        mutableStateOf(
            context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
                .getString("preferred_language", "hi") ?: "hi"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Progress Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val isActive = i == currentStep
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isActive) 24.dp else 8.dp, 6.dp)
                            .clip(GxShapePill)
                            .background(if (isActive) GxPrimary else GxBorder)
                    )
                }
            }

            // Step Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                when (currentStep) {
                    // Step 0: Welcome
                    0 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(
                            color = GxPrimarySoft,
                            shape = GxShapeMd,
                            border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Text(
                            "Protection that speaks your language",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GxTextHi,
                            lineHeight = 34.sp
                        )
                        Text(
                            "Hello $name. Guardian detects scam calls, coercive extortion, and phishing links in real time across Hindi, English, and 10+ Indian regional languages.",
                            color = GxTextMid,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    }

                    // Step 1: Real-Time Interception Permissions
                    1 -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Security Layers", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                            Text(
                                "Grant core hardware hooks so Guardian can protect your device.",
                                color = GxTextMid,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "1. Microphone Speech-to-Text",
                                desc = "Real-time acoustic analysis of speakerphone calls for extortion and digital arrest threats.",
                                isGranted = hasMicPermission,
                                onGrant = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "2. Phone Call Telephony",
                                desc = "Auto-triggers the AI Reasoning Card as soon as a call is ringing or answered.",
                                isGranted = hasPhonePermission,
                                onGrant = { phoneLauncher.launch(android.Manifest.permission.READ_PHONE_STATE) }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "3. Floating Heads-Up Overlay",
                                desc = "Renders the live risk telemetry over the stock dialer and messaging apps.",
                                isGranted = hasOverlayPermission,
                                onGrant = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "4. Call Control & Auto-Hangup",
                                desc = "Allows one-tap emergency call termination when severe fraud (score >= 85%) is detected.",
                                isGranted = hasCallControlPermission,
                                onGrant = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        callControlLauncher.launch(android.Manifest.permission.ANSWER_PHONE_CALLS)
                                    } else {
                                        hasCallControlPermission = true
                                    }
                                }
                            )
                        }
                        item {
                            OnboardingPermRow(
                                title = "5. Notification Alerts",
                                desc = "Displays critical scam heads-up warnings during active attacks.",
                                isGranted = hasNotificationPermission,
                                onGrant = {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        hasNotificationPermission = true
                                    }
                                }
                            )
                        }
                    }

                    // Step 2: Language Picker
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Scam Warning Language", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                        Text(
                            "Select the language you want Guardian's Bhashini AI voice alerts and reasoning card explanations in:",
                            color = GxTextMid,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        val languages = listOf(
                            "hi" to "Hindi (हिन्दी)",
                            "ta" to "Tamil (தமிழ்)",
                            "te" to "Telugu (తెలుగు)",
                            "bn" to "Bengali (বাংলা)",
                            "mr" to "Marathi (मराठी)",
                            "kn" to "Kannada (ಕನ್ನಡ)",
                            "ml" to "Malayalam (മലയാളം)",
                            "pa" to "Punjabi (ਪੰਜਾਬੀ)",
                            "gu" to "Gujarati (ગુજરાતી)",
                            "en" to "English"
                        )

                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            languages.forEach { (code, label) ->
                                val isSelected = selectedLang == code
                                GxChip(
                                    text = label,
                                    variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                                    height = 36.dp,
                                    onClick = {
                                        selectedLang = code
                                        context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
                                            .edit()
                                            .putString("preferred_language", code)
                                            .apply()
                                    }
                                )
                            }
                        }
                    }

                    // Step 3: Finish Screen
                    3 -> Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = GxSafeSoft,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, GxSafe.copy(alpha = 0.5f)),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = GxSafe, modifier = Modifier.size(36.dp))
                            }
                        }
                        Text("You're Protected", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                        Text(
                            "Dual-Engine AI (Gemini 1.5 Flash + Bhashini STT) is standing by in your secure on-device sandbox.",
                            color = GxTextMid,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Bottom Navigation Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0 && currentStep < 3) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back", color = GxTextMid, fontSize = 14.sp)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                if (currentStep < 3) {
                    GxButton.Primary(
                        text = if (currentStep == 0) "Get Started" else "Continue",
                        onClick = { currentStep++ },
                        icon = Icons.AutoMirrored.Filled.ArrowForward
                    )
                } else {
                    GxButton.Primary(
                        text = "Open Guardian Shield",
                        onClick = onComplete,
                        icon = Icons.Default.Shield,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    GxCard(
        backgroundColor = if (isGranted) GxSurfaceAlt else GxSurface,
        borderColor = if (isGranted) GxSafe.copy(alpha = 0.3f) else GxBorder,
        contentPadding = 14.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (isGranted) GxSafeSoft else GxSurfaceAlt, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGranted) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isGranted) GxSafe else GxWarning,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = GxTextHi)
                Text(desc, color = GxTextLo, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.width(8.dp))

            if (isGranted) {
                GxChip(text = "ACTIVE", variant = GxChipVariant.Safe, height = 24.dp)
            } else {
                GxButton.Primary(
                    text = "Grant",
                    onClick = onGrant,
                    height = 34.dp
                )
            }
        }
    }
}

// ==========================================
// 3. HOME SCREEN (Linear / Arc / 1Password Aesthetic)
// ==========================================
@Composable
fun HomeScreen(
    state: GuardianState,
    contentPadding: PaddingValues,
    onOpenCallRisk: () -> Unit,
    onOpenQrScanner: () -> Unit = {},
    onOpenCallHistory: () -> Unit = {},
    onProtectionChange: (Boolean) -> Unit,
    onAddIncident: (GuardianIncident) -> Unit
) {
    var showIncidentDialog by remember { mutableStateOf(false) }
    var showLinkCheckDialog by remember { mutableStateOf(false) }
    var showMsgCheckDialog by remember { mutableStateOf(false) }
    var showLinkBarrierScreen by remember { mutableStateOf(false) }
    var showMessageScanScreen by remember { mutableStateOf(false) }
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var showLlamaModal by remember { mutableStateOf(false) }
    var showWhisperModal by remember { mutableStateOf(false) }
    var showTrustedContactScreen by remember { mutableStateOf(false) }
    var showNetworkShieldScreen by remember { mutableStateOf(false) }
    var showApkRiskScreen by remember { mutableStateOf(false) }
    var showVoiceClipScreen by remember { mutableStateOf(false) }
    var showOfflineDemoScreen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    if (showOfflineDemoScreen) {
        com.susagi.ui.demo.OfflineDemoScreen(
            onBack = { showOfflineDemoScreen = false }
        )
        return
    }

    if (showNetworkShieldScreen) {
        com.susagi.ui.network.NetworkShieldScreen(
            onBack = { showNetworkShieldScreen = false }
        )
        return
    }

    if (showApkRiskScreen) {
        com.susagi.ui.apk.ApkRiskScannerScreen(
            onBack = { showApkRiskScreen = false }
        )
        return
    }

    if (showVoiceClipScreen) {
        com.susagi.ui.voice.VoiceClipScannerScreen(
            onBack = { showVoiceClipScreen = false }
        )
        return
    }

    if (showLinkBarrierScreen) {
        com.susagi.ui.LinkSafetyBarrierScreen(
            onBack = { showLinkBarrierScreen = false },
            onOpenUrl = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(ctx, "No compatible browser found to open link", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
        return
    }

    if (showMessageScanScreen) {
        com.susagi.ui.MessageScanScreen(
            onBack = { showMessageScanScreen = false },
            onInspectLink = { url ->
                showLinkBarrierScreen = true
            }
        )
        return
    }

    if (showTrustedContactScreen) {
        com.guardian.app.protect.advanced.TrustedContactSettingsScreen(
            onBack = { showTrustedContactScreen = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ----------------------------------------------------
        // Profile & Trusted Contact Status Header
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GxPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                state.name.take(2).uppercase().ifBlank { "TG" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${state.name}'s Guardian",
                            color = GxTextHi,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "✓ On-Device AI Active",
                            color = GxSafe,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapePill,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier.clickable { com.susagi.ui.localization.AppLanguageState.toggleLanguage() }
                    ) {
                        Text(
                            text = com.susagi.ui.localization.AppLanguageState.currentLanguage.displayName,
                            color = GxPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = GxSafeSoft,
                        shape = GxShapePill,
                        border = BorderStroke(1.dp, GxSafe.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { showTrustedContactScreen = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = GxSafe, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mom Linked", color = GxSafe, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Quo-Inspired Defense Status Hero (Pure White Surface)
        // ----------------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeLg,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = GxSafeSoft,
                            shape = GxShapePill,
                            border = BorderStroke(1.dp, GxSafe.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GxLiveDot(
                                    color = GxSafe,
                                    size = 6.dp,
                                    pulsing = state.protectionEnabled
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.protectionEnabled) "LOCAL SHIELDS ACTIVE" else "SHIELDS PAUSED",
                                    color = if (state.protectionEnabled) GxSafe else GxTextLo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Switch(
                            checked = state.protectionEnabled,
                            onCheckedChange = { onProtectionChange(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GxPrimary,
                                uncheckedThumbColor = GxTextLo,
                                uncheckedTrackColor = GxSurfaceAlt
                            )
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        if (state.protectionEnabled) "On-Device Threat Defense" else "Defense Inactive",
                        color = GxTextHi,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        if (state.protectionEnabled)
                            "Deterministic offline analysis defending against digital arrest extortion, electricity disconnection fraud, and phishing links."
                        else "Sensor listeners and background threat inspection are paused. Toggle switch to resume.",
                        color = GxTextMid,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = { showLinkBarrierScreen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = GxShapeMd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GxPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Check a Message or Link",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Compact Supported-App Share Guidance Card
        // ----------------------------------------------------
        item {
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        color = GxPrimary.copy(alpha = 0.08f),
                        shape = GxShapeSm,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            "Instant Share-to-SuSagi",
                            color = GxTextHi,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Highlight text in WhatsApp or SMS → Tap Share → Select SuSagi Guardian for instant offline inspection.",
                            color = GxTextMid,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Quo-Style Calling Hub Card
        // ----------------------------------------------------
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeLg,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = GxPrimary.copy(alpha = 0.08f),
                                shape = GxShapeSm,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Protected Voice Calling",
                                    color = GxTextHi,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "E2EE VoIP with live acoustic threat detector",
                                    color = GxTextLo,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            color = GxSafeSoft,
                            shape = GxShapePill,
                            border = BorderStroke(0.5.dp, GxSafe.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "READY",
                                color = GxSafe,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ctx.startActivity(Intent(ctx, com.guardian.app.voip.RoomCodeActivity::class.java))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GxPrimary,
                                contentColor = Color.White
                            ),
                            shape = GxShapeSm,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Host / Join Call", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onOpenCallHistory,
                            border = BorderStroke(1.dp, GxBorder),
                            shape = GxShapeSm,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = GxTextMid, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Call History", fontSize = 12.sp, color = GxTextHi, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Section Header: "PROTECTION MODULES"
        // ----------------------------------------------------
        item {
            Text(
                "ACTIVE DEFENSE MODULES",
                color = GxTextLo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ----------------------------------------------------
        // Linear Protection Modules List
        // ----------------------------------------------------
        item {
            FeatureCardItem(
                title = "Live Call Speaker Guard",
                subtitle = "Continuous 16kHz Bhashini STT + Gemini reasoning with tone & TTS warning",
                icon = Icons.Default.PhoneInTalk,
                accentColor = GxPrimary,
                statusBadge = "MONITORING",
                onClick = onOpenCallRisk
            )
        }

        item {
            FeatureCardItem(
                title = "Camera QR Vision Shield",
                subtitle = "Real-time Google ML Kit scanner to prevent malicious UPI/URL redirects",
                icon = Icons.Default.QrCodeScanner,
                accentColor = GxSafe,
                statusBadge = "READY",
                onClick = onOpenQrScanner
            )
        }

        item {
            FeatureCardItem(
                title = "Link Safety Barrier",
                subtitle = "Zero-trust browser protection intercepting URLs to prevent phishing & malware APKs",
                icon = Icons.Default.Link,
                accentColor = GxSafe,
                statusBadge = "READY",
                onClick = { showLinkBarrierScreen = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Regional Message Scanner",
                subtitle = "Screen SMS and chat messages for digital arrest, electricity bill scams & lottery traps",
                icon = Icons.Default.Security,
                accentColor = GxPrimary,
                statusBadge = "READY",
                onClick = { showMessageScanScreen = true }
            )
        }

        item {
            val modelMgr = remember { com.susagi.edgeai.ModelManager(ctx) }
            val isReady = remember(showLlamaModal) { modelMgr.isReady() }
            FeatureCardItem(
                title = "On-Device Llama Sentinel (Arm)",
                subtitle = "Quantized Llama 3.2 1B running locally with ExecuTorch for zero-cloud privacy",
                icon = Icons.Default.Psychology,
                accentColor = GxPrimary,
                statusBadge = if (isReady) "LLAMA READY" else "DOWNLOAD NEEDED",
                onClick = { showLlamaModal = true }
            )
        }

        item {
            val whisperMgr = remember { com.susagi.stt.whisper.WhisperModelManager(ctx) }
            val isWhisperReady = remember(showWhisperModal) { whisperMgr.isReady() }
            FeatureCardItem(
                title = "Offline Whisper Speech AI (Arm)",
                subtitle = "Quantized Whisper Small INT8 with ExecuTorch for offline scam call transcription",
                icon = Icons.Default.Mic,
                accentColor = GxSafe,
                statusBadge = if (isWhisperReady) "WHISPER READY" else "DOWNLOAD NEEDED",
                onClick = { showWhisperModal = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Scam Call Audit Log",
                subtitle = "Chronological threat records & one-tap 1930 Cybercrime filing",
                icon = Icons.Default.History,
                accentColor = GxDanger,
                statusBadge = "ONLINE",
                onClick = onOpenCallHistory
            )
        }

        item {
            FeatureCardItem(
                title = "Emergency Saved Contact & Family Alert",
                subtitle = "Auto-dispatches SMS & instant Ably alert to saved trusted contact on fraud",
                icon = Icons.Default.Person,
                accentColor = GxSafe,
                statusBadge = "ACTIVE",
                onClick = { showTrustedContactScreen = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Call Screening & Auto-Block",
                subtitle = "Telecom service rejecting blacklisted scam and extortion numbers",
                icon = Icons.Default.Block,
                accentColor = GxPrimary,
                statusBadge = "ENABLED",
                onClick = onOpenCallHistory
            )
        }

        item {
            FeatureCardItem(
                title = "Network Flow Anomaly Shield",
                subtitle = "Import NetFlow / CICIDS CSV telemetry to inspect C2 beaconing and port scans",
                icon = Icons.Default.Security,
                accentColor = GxPrimary,
                statusBadge = "READY",
                onClick = { showNetworkShieldScreen = true }
            )
        }

        item {
            FeatureCardItem(
                title = "APK & Sideload Risk Inspector",
                subtitle = "Examine unverified app packages and dangerous SMS/Accessibility permissions",
                icon = Icons.Default.Security,
                accentColor = GxDanger,
                statusBadge = "READY",
                onClick = { showApkRiskScreen = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Voice Clip Threat Analyzer",
                subtitle = "Standalone speech-to-text & acoustic extortion analyzer independent of calls",
                icon = Icons.Default.Mic,
                accentColor = GxSafe,
                statusBadge = "READY",
                onClick = { showVoiceClipScreen = true }
            )
        }

        item {
            FeatureCardItem(
                title = "Offline Demonstration Panel",
                subtitle = "Live test scenarios calling production SafetyBarrier & Network engines",
                icon = Icons.Default.WarningAmber,
                accentColor = GxPrimary,
                statusBadge = "6 SCENARIOS",
                onClick = { showOfflineDemoScreen = true }
            )
        }

        // Incident Report Quick CTA
        item {
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showIncidentDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = GxWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Report Suspicious Call or Number", color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Submit fraud details to train local AI defenses", color = GxTextLo, fontSize = 11.sp)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = GxTextLo,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Modal Dialogs
    if (showIncidentDialog) {
        IncidentReportModal(
            onDismiss = { showIncidentDialog = false },
            onSave = {
                onAddIncident(it)
                showIncidentDialog = false
            }
        )
    }

    if (showLinkCheckDialog) {
        LinkCheckerModal(onDismiss = { showLinkCheckDialog = false })
    }

    if (showMsgCheckDialog) {
        MessageCheckerModal(onDismiss = { showMsgCheckDialog = false })
    }

    if (showQrScannerDialog) {
        QrScannerModal(onDismiss = { showQrScannerDialog = false })
    }

    if (showLlamaModal) {
        LlamaModelProvisioningModal(onDismiss = { showLlamaModal = false })
    }

    if (showWhisperModal) {
        WhisperModelProvisioningModal(onDismiss = { showWhisperModal = false })
    }
}

@Composable
private fun CommandGridCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    GxCard(
        backgroundColor = GxSurface,
        borderColor = GxBorder,
        contentPadding = 14.dp,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = GxShapeSm,
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    color = badgeColor.copy(alpha = 0.1f),
                    shape = GxShapePill,
                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        badge,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = GxTextLo, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FeatureCardItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    statusBadge: String = "ACTIVE",
    onClick: () -> Unit
) {
    GxCard(
        backgroundColor = GxSurface,
        borderColor = GxBorder,
        contentPadding = 16.dp,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.12f),
                shape = GxShapeMd,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = GxShapePill
                    ) {
                        Text(
                            statusBadge,
                            color = accentColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = GxTextLo, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GxTextLo,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==========================================
// 4. EVENTS SCREEN (Audit Log)
// ==========================================
@Composable
fun EventsScreen(events: List<GuardianEvent>, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Security Audit Log", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
            Text("Chronological record of background telemetry checks.", color = GxTextMid, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (events.isEmpty()) {
            item {
                GxCard(modifier = Modifier.fillMaxWidth()) {
                    Text("No security events logged yet.", color = GxTextLo, fontSize = 13.sp)
                }
            }
        } else {
            items(events) { event ->
                GxCard(
                    backgroundColor = GxSurface,
                    contentPadding = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GxLiveDot(
                            color = if (event.safe) GxSafe else GxDanger,
                            size = 8.dp,
                            pulsing = false
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(event.title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(event.detail, color = GxTextMid, fontSize = 12.sp)
                        }
                        Text(event.time, color = GxTextLo, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. INCIDENTS SCREEN (Threat Log)
// ==========================================
@Composable
fun IncidentsScreen(
    incidents: List<GuardianIncident>,
    contentPadding: PaddingValues,
    onAddIncident: (GuardianIncident) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Threat Log", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                    Text("Flagged scams, extortions, and blocked callers.", color = GxTextMid, fontSize = 13.sp)
                }
                GxButton.Primary(
                    text = "Report",
                    onClick = { showAddDialog = true },
                    height = 38.dp
                )
            }
        }

        if (incidents.isEmpty()) {
            item {
                GxCard(
                    backgroundColor = GxSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = GxSafe, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No threats logged", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                        Text("Your phone is currently clear of flagged extortion attempts.", color = GxTextLo, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(incidents) { incident ->
                GxCard(
                    backgroundColor = GxDangerSoft,
                    borderColor = GxDanger.copy(alpha = 0.4f),
                    contentPadding = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(incident.title, color = GxTextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(incident.detail, color = GxTextMid, fontSize = 12.sp)
                        }
                        GxChip(text = incident.risk, variant = GxChipVariant.Danger, height = 24.dp)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        IncidentReportModal(
            onDismiss = { showAddDialog = false },
            onSave = {
                onAddIncident(it)
                showAddDialog = false
            }
        )
    }
}

// ==========================================
// 6. SETTINGS SCREEN
// ==========================================
enum class SettingType { All, Phone, Messages, Links }

@Composable
fun SettingsScreen(
    state: GuardianState,
    contentPadding: PaddingValues,
    onToggle: (SettingType, Boolean) -> Unit,
    onDeleteData: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE) }
    var currentLang by remember {
        mutableStateOf(prefs.getString("preferred_language", "hi") ?: "hi")
    }
    var voiceSynthEnabled by remember {
        mutableStateOf(prefs.getBoolean("voice_synthesis_enabled", true))
    }
    var plainReasoningEnabled by remember {
        mutableStateOf(prefs.getBoolean("plain_reasoning_enabled", true))
    }

    var showTrustedContact by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showLlamaModal by remember { mutableStateOf(false) }
    var showWhisperModal by remember { mutableStateOf(false) }

    if (showLlamaModal) {
        LlamaModelProvisioningModal(onDismiss = { showLlamaModal = false })
    }

    if (showWhisperModal) {
        WhisperModelProvisioningModal(onDismiss = { showWhisperModal = false })
    }

    if (showTrustedContact) {
        com.guardian.app.protect.advanced.TrustedContactSettingsScreen(
            onBack = { showTrustedContact = false }
        )
        return
    }

    if (showProfiles) {
        com.guardian.app.protect.advanced.ContactProfilesScreen(
            onBack = { showProfiles = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings & Sensors", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
            Text("Configure proactive detection engines and privacy preferences.", color = GxTextMid, fontSize = 13.sp)
        }

        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Interception Channels", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)

                    SettingToggleRow(
                        title = "Live Call STT Analyzer",
                        desc = "Monitor live speakerphone speech for scams",
                        enabled = state.phoneProtection,
                        onToggle = { onToggle(SettingType.Phone, it) }
                    )

                    SettingToggleRow(
                        title = "SMS & Notification Shield",
                        desc = "Detect urgent extortion in WhatsApp & SMS",
                        enabled = state.messageProtection,
                        onToggle = { onToggle(SettingType.Messages, it) }
                    )

                    SettingToggleRow(
                        title = "Anti-Phishing Link Shield",
                        desc = "Pre-screen clicked links before opening browser",
                        enabled = state.linkProtection,
                        onToggle = { onToggle(SettingType.Links, it) }
                    )
                }
            }
        }

        // Advanced Protection Suite (New)
        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Advanced Protection Suite", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                        GxChip(text = "PRO 2026", variant = GxChipVariant.Brand)
                    }

                    SettingToggleRow(
                        title = "Voice Synthesis Detection",
                        desc = "Acoustic FFT & pitch variance analysis to detect cloned/AI voices",
                        enabled = voiceSynthEnabled,
                        onToggle = {
                            voiceSynthEnabled = it
                            prefs.edit().putBoolean("voice_synthesis_enabled", it).apply()
                        }
                    )

                    SettingToggleRow(
                        title = "Plain-Language Reasoning",
                        desc = "Clear English & Hindi breakdown explaining risk score factors",
                        enabled = plainReasoningEnabled,
                        onToggle = {
                            plainReasoningEnabled = it
                            prefs.edit().putBoolean("plain_reasoning_enabled", it).apply()
                        }
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTrustedContact = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Emergency Trusted Contact", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Auto-alert relative on >=75% severe scam call risk", color = GxTextLo, fontSize = 11.sp)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProfiles = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Identity Consistency Baselines", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Behavioral profiles for family & bank imposter detection", color = GxTextLo, fontSize = 11.sp)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }
                }
            }
        }

        // On-Device Edge AI Models (Arm ExecuTorch PS3)
        item {
            val llamaModelsDir = remember { java.io.File(context.filesDir, "models") }
            val llamaModelFile = remember { java.io.File(llamaModelsDir, com.susagi.edgeai.ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelFileName) }
            val isLlamaReady = llamaModelFile.exists() && llamaModelFile.length() > 50_000_000L

            val whisperDir = remember { java.io.File(context.filesDir, "whisper") }
            val whisperModelFile = remember { java.io.File(whisperDir, com.susagi.edgeai.ModelManifest.WHISPER_SMALL_ARM.modelFileName) }
            val isWhisperReady = whisperModelFile.exists() && whisperModelFile.length() > 30_000_000L

            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("On-Device AI Models", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                        }
                        GxChip(text = "Arm ExecuTorch", variant = GxChipVariant.Brand)
                    }
                    Text(
                        "100% on-device inference running locally in Airplane Mode on Arm Cortex/NPU hardware. Zero cloud tokens or speech data sent externally.",
                        color = GxTextLo,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLlamaModal = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Llama 3.2 1B Instruct", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isLlamaReady) "SpinQuant Int4 XNNPACK (Ready on device)"
                                else "Not Provisioned (Tap to inspect or download)",
                                color = if (isLlamaReady) GxSafe else GxTextLo,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWhisperModal = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Whisper Speech AI", color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (isWhisperReady) "Arm Whisper Small Int8 (Ready on device)"
                                else "Not Provisioned (Tap to inspect or download)",
                                color = if (isWhisperReady) GxSafe else GxTextLo,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GxTextMid
                        )
                    }
                }
            }
        }

        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Language Preference (Bhashini AI)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                    Text("Select warning and reasoning language:", color = GxTextLo, fontSize = 12.sp)

                    val languages = listOf(
                        "hi" to "Hindi (हिन्दी)",
                        "ta" to "Tamil (தமிழ்)",
                        "te" to "Telugu (తెలుగు)",
                        "bn" to "Bengali (বাংলা)",
                        "mr" to "Marathi (मराठी)",
                        "kn" to "Kannada (ಕನ್ನಡ)",
                        "ml" to "Malayalam (മലയാളം)",
                        "pa" to "Punjabi (ਪੰਜਾਬੀ)",
                        "gu" to "Gujarati (ગુજરાતી)",
                        "en" to "English"
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        languages.forEach { (code, label) ->
                            val isSelected = currentLang == code
                            GxChip(
                                text = label,
                                variant = if (isSelected) GxChipVariant.Brand else GxChipVariant.Neutral,
                                onClick = {
                                    currentLang = code
                                    prefs.edit().putString("preferred_language", code).apply()
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            GxCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Android Platform Boundaries & Privacy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GxTextHi)
                    }
                    Text(
                        "• SMS & Notifications: SuSagi evaluates accessible notification content only when granted explicit permission by the user in Android Settings. It does not access private chat databases of WhatsApp or other apps.\n• Link Shield: Intercepts tapped links and verifies domain legitimacy, Punycode spoofing, and APK traps before handing off to your browser.\n• Zero Cloud Dependency: Core threat evaluation runs directly on-device using Arm Cortex/NPU hardware acceleration.",
                        color = GxTextLo,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            GxButton.Ghost(
                text = "Sign Out & Reset Session",
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // App Version & Quick Seed Button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch(Dispatchers.IO) {
                            com.guardian.app.protect.advanced.DemoSeed.seedDemoData(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(
                                    context,
                                    "✨ Demo data initialized (Mom profile & Trusted Contact active)",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Guardian v2.0 • Advanced Protection Engine",
                        style = GxType.caption,
                        color = GxTextLo
                    )
                    Text(
                        "Tap to re-seed hackathon demo baseline data",
                        style = GxType.caption,
                        color = GxPrimary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    desc: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GxTextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = GxTextLo, fontSize = 11.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GxPrimary,
                uncheckedThumbColor = GxTextLo,
                uncheckedTrackColor = GxSurfaceAlt
            )
        )
    }
}

// ==========================================
// 7. MODALS
// ==========================================
@Composable
fun LinkCheckerModal(onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<LinkVerdict?>(null) }
    var inferenceResult by remember { mutableStateOf<com.susagi.edgeai.InferenceResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Link Shield Inspector", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Inspect clicked links or suspicious SMS/WhatsApp URLs against known phishing patterns, malware APK sideloads, and punycode spoofing.",
                    fontSize = 12.sp,
                    color = GxTextLo,
                    lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://example.com/login-kyc", color = GxTextLo) },
                    singleLine = true,
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )

                if (verdict != null) {
                    val v = verdict!!
                    val isDangerous = v.status == RiskStatus.High
                    val isSuspicious = v.status == RiskStatus.Suspicious
                    val accentColor = when {
                        isDangerous -> GxDanger
                        isSuspicious -> GxWarning
                        else -> GxSafe
                    }
                    val bgSoft = when {
                        isDangerous -> GxDangerSoft
                        isSuspicious -> GxWarningSoft
                        else -> GxSafeSoft
                    }

                    GxCard(
                        backgroundColor = bgSoft,
                        borderColor = accentColor.copy(alpha = 0.5f),
                        contentPadding = 14.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when {
                                        isDangerous -> "RISK SCORE: 95%"
                                        isSuspicious -> "RISK SCORE: 65%"
                                        else -> "RISK SCORE: 8%"
                                    },
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Surface(
                                    color = accentColor.copy(alpha = 0.2f),
                                    shape = GxShapePill,
                                    border = BorderStroke(0.5.dp, accentColor)
                                ) {
                                    Text(
                                        when {
                                            isDangerous -> "MALICIOUS"
                                            isSuspicious -> "SUSPICIOUS"
                                            else -> "CLEAN"
                                        },
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(v.title, color = GxTextHi, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(v.detail, color = GxTextMid, fontSize = 11.sp, lineHeight = 15.sp)

                            if (inferenceResult != null) {
                                val inf = inferenceResult!!
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Llama Edge Assessment (${inf.analysisSource}):",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(inf.explanation, color = GxTextHi, fontSize = 11.sp, lineHeight = 15.sp)
                                if (inf.recommendedActions.isNotEmpty()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "Action Plan: ${inf.recommendedActions.joinToString("; ")}",
                                        color = GxTextLo,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = if (isChecking) "Analyzing..." else "Analyze URL",
                onClick = {
                    if (url.isNotBlank()) {
                        isChecking = true
                        verdict = LinkCheckActivity.evaluateLinkSafety(url)
                        scope.launch {
                            try {
                                inferenceResult = com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).analyzeUrl(url)
                            } catch (_: Exception) {}
                            isChecking = false
                        }
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = GxTextMid) }
        }
    )
}

@Composable
fun MessageCheckerModal(onDismiss: () -> Unit) {
    var messageText by remember { mutableStateOf("") }
    var inferenceResult by remember { mutableStateOf<com.susagi.edgeai.InferenceResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = GxDanger, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Message & SMS Scam Shield", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "On-device Llama AI analyzes suspicious SMS, WhatsApp, and social engineering messages for extortion, fake KYC, and digital arrest threats.",
                    fontSize = 12.sp,
                    color = GxTextLo,
                    lineHeight = 16.sp
                )

                // Quick test scenario chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapePill,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier.clickable {
                            messageText = "FIR registered by cyber crime police. You are under digital arrest. Transfer 50000 rupees immediately or face arrest."
                        }
                    ) {
                        Text("🚨 Digital Arrest", color = GxDanger, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapePill,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier.clickable {
                            messageText = "Dear Consumer, your electricity power cut will be done tonight at 9:30 PM due to unpaid bill. Call electricity officer immediately at 9876543210."
                        }
                    ) {
                        Text("⚡ Power Cut", color = GxWarning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }

                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapePill,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier.clickable {
                            messageText = "Dear customer, your SBI net banking account is blocked. Update your PAN card on this link immediately: https://sbi-portal-verify.top"
                        }
                    ) {
                        Text("🏦 Bank KYC", color = GxPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Paste SMS or WhatsApp message here...", color = GxTextLo) },
                    maxLines = 4,
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )

                if (inferenceResult != null) {
                    val res = inferenceResult!!
                    val isDangerous = res.riskScore >= 70
                    val isSuspicious = res.riskScore in 40..69
                    val accentColor = when {
                        isDangerous -> GxDanger
                        isSuspicious -> GxWarning
                        else -> GxSafe
                    }
                    val bgSoft = when {
                        isDangerous -> GxDangerSoft
                        isSuspicious -> GxWarningSoft
                        else -> GxSafeSoft
                    }

                    GxCard(
                        backgroundColor = bgSoft,
                        borderColor = accentColor.copy(alpha = 0.5f),
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "RISK SCORE: ${res.riskScore}%",
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Surface(
                                    color = accentColor.copy(alpha = 0.2f),
                                    shape = GxShapePill,
                                    border = BorderStroke(0.5.dp, accentColor)
                                ) {
                                    Text(
                                        res.analysisSource,
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                "Threat: ${res.threatType.replace('_', ' ').uppercase()}",
                                color = GxTextHi,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(res.explanation, color = GxTextMid, fontSize = 11.sp, lineHeight = 15.sp)

                            if (res.recommendedActions.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("RECOMMENDED ACTIONS:", color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                res.recommendedActions.forEach { act ->
                                    Text("• $act", color = GxTextHi, fontSize = 11.sp, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = if (isChecking) "Analyzing..." else "Analyze Message",
                onClick = {
                    if (messageText.isNotBlank()) {
                        isChecking = true
                        scope.launch {
                            try {
                                inferenceResult = com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).analyzeMessage(messageText)
                            } catch (_: Exception) {}
                            isChecking = false
                        }
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = GxTextMid) }
        }
    )
}

@Composable
fun IncidentReportModal(
    onDismiss: () -> Unit,
    onSave: (GuardianIncident) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var risk by remember { mutableStateOf("High") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = { Text("Report Threat / Scam Number", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Phone Number / Threat Title", color = GxTextLo) },
                    singleLine = true,
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Incident Details (e.g. impersonated CBI)", color = GxTextLo) },
                    colors = customGxTextFieldColors(),
                    shape = GxShapeMd,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = "Save Incident",
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            GuardianIncident(
                                title = title,
                                detail = detail.ifBlank { "Flagged suspicious activity" },
                                time = "Just now",
                                risk = risk
                            )
                        )
                    }
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GxTextMid) }
        }
    )
}

@Composable
fun QrScannerModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        title = { Text("QR Vision Shield") },
        text = {
            Text("Open the dedicated camera ML vision scanner to inspect QR codes for malware payloads.", color = GxTextMid)
        },
        confirmButton = {
            GxButton.Primary(
                text = "Launch Scanner",
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, QrScannerActivity::class.java))
                },
                height = 42.dp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = GxTextMid) }
        }
    )
}

@Composable
fun customGxTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GxSurfaceAlt,
    unfocusedContainerColor = GxSurface,
    focusedBorderColor = GxPrimary,
    unfocusedBorderColor = GxBorder,
    focusedTextColor = GxTextHi,
    unfocusedTextColor = GxTextHi,
    cursorColor = GxPrimary
)

fun isDefaultBrowser(context: Context): Boolean {
    return try {
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolver = context.packageManager
        val defaultHandler = resolver.resolveActivity(testIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        defaultHandler?.activityInfo?.packageName == context.packageName
    } catch (_: Exception) {
        false
    }
}

@Composable
fun LlamaModelProvisioningModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = remember { com.susagi.edgeai.ModelManager(context) }

    var modelState by remember { mutableStateOf(modelManager.getModelState()) }
    var hardwareSummary by remember { mutableStateOf(modelManager.getHardwareSummary()) }
    var downloadProgress by remember { mutableStateOf<com.susagi.edgeai.ModelManager.DownloadProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    var benchmarkPrompt by remember {
        mutableStateOf("URGENT: This is Inspector Sharma from Cyber Crime. An arrest warrant is issued. Transfer ₹50,000 immediately to verify your account or face arrest.")
    }
    var benchmarkResult by remember { mutableStateOf<com.susagi.edgeai.NativeInferenceBenchmark?>(null) }
    var isRunningBenchmark by remember { mutableStateOf(false) }

    // Pickers for manual file import
    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = modelManager.importFromUri(uri, isTokenizer = false)
                if (ok) {
                    com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).llamaEngine.let {
                        (it as? com.susagi.edgeai.ExecuTorchLlamaEngine)?.reinitialize()
                    }
                    modelState = modelManager.getModelState()
                    hardwareSummary = modelManager.getHardwareSummary()
                }
            }
        }
    }

    val tokenizerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = modelManager.importFromUri(uri, isTokenizer = true)
                if (ok) {
                    com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).llamaEngine.let {
                        (it as? com.susagi.edgeai.ExecuTorchLlamaEngine)?.reinitialize()
                    }
                    modelState = modelManager.getModelState()
                    hardwareSummary = modelManager.getHardwareSummary()
                }
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = GxPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "On-Device Llama Sentinel",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextHi
                    )
                    Text(
                        "Arm Bharat AI-SoC Challenge PS3 · Zero-Cloud Privacy",
                        fontSize = 10.sp,
                        color = GxTextLo
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Hardware & Storage Architecture Card
                GxCard(
                    backgroundColor = GxSurfaceAlt,
                    borderColor = GxBorder,
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CPU Architecture:", fontSize = 11.sp, color = GxTextLo)
                            Text(
                                if (hardwareSummary["is_arm64"] == "true") "Arm64-v8a/v9a (Accelerated)" else hardwareSummary["cpu_abi"].orEmpty(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxSafe
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available RAM:", fontSize = 11.sp, color = GxTextLo)
                            Text(
                                "${hardwareSummary["available_ram_mb"]} MB / ${hardwareSummary["total_ram_mb"]} MB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxTextHi
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mobile Free Storage:", fontSize = 11.sp, color = GxTextLo)
                            val freeMb = hardwareSummary["storage_free_mb"]?.toLongOrNull() ?: 0L
                            Text(
                                if (freeMb > 1024) String.format("%.1f GB Free", freeMb / 1024f) else "$freeMb MB Free",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxTextHi
                            )
                        }
                        HorizontalDivider(color = GxBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Llama Model (.pte):", fontSize = 11.sp, color = GxTextLo)
                            val mFile = modelManager.modelFile
                            Text(
                                if (mFile.exists() && mFile.length() > 50_000_000L) "${mFile.name} (${mFile.length() / (1024 * 1024)} MB)" else "Not Downloaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mFile.exists()) GxSafe else GxDanger
                            )
                        }
                        val mFile = modelManager.modelFile
                        if (mFile.exists() && mFile.length() > 50_000_000L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Model SHA-256:", fontSize = 10.sp, color = GxTextLo)
                                val hash = remember(mFile.length()) { modelManager.computeSha256(mFile) }
                                Text(
                                    if (hash.length > 20) "${hash.take(12)}...${hash.takeLast(8)}" else hash,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = GxSafe
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tokenizer:", fontSize = 11.sp, color = GxTextLo)
                            val tFile = modelManager.tokenizerFile
                            Text(
                                if (tFile.exists() && tFile.length() > 1024L) "${tFile.name} (${tFile.length() / 1024} KB)" else "Not Downloaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tFile.exists()) GxSafe else GxDanger
                            )
                        }
                    }
                }

                // 2. Direct-to-Phone Download Card
                val isModelReady = modelState == com.susagi.edgeai.ModelManager.ModelState.READY
                if (!isModelReady) {
                    GxCard(
                        backgroundColor = GxPrimarySoft.copy(alpha = 0.5f),
                        borderColor = GxPrimary.copy(alpha = 0.4f),
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "PROVISION ON-DEVICE MODEL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxPrimary
                            )
                            Text(
                                "Download quantized Llama 3.2 1B (1.08 GB) directly to your mobile storage for offline threat detection.",
                                fontSize = 11.sp,
                                color = GxTextMid,
                                lineHeight = 15.sp
                            )

                            if (isDownloading) {
                                val p = downloadProgress
                                if (p != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { (p.percent / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = GxPrimary,
                                            trackColor = GxSurfaceAlt
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${p.percent.toInt()}% (${p.bytesDownloaded / (1024 * 1024)}MB / ${p.totalBytes / (1024 * 1024)}MB)",
                                                fontSize = 10.sp,
                                                color = GxTextHi
                                            )
                                            Text(
                                                String.format("%.1f MB/s", p.speedMbPerSec),
                                                fontSize = 10.sp,
                                                color = GxSafe
                                            )
                                        }
                                        Text(
                                            "Downloading: ${p.currentFileName}",
                                            fontSize = 9.sp,
                                            color = GxTextLo
                                        )
                                        if (p.error != null) {
                                            Text("Error: ${p.error}", fontSize = 10.sp, color = GxDanger)
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GxPrimary, strokeWidth = 2.dp)
                                        Text("Initializing download connection...", fontSize = 11.sp, color = GxTextMid)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        scope.launch {
                                            val ok = modelManager.downloadModelAndTokenizer { progress ->
                                                downloadProgress = progress
                                            }
                                            isDownloading = false
                                            if (ok) {
                                                com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).llamaEngine.let {
                                                    (it as? com.susagi.edgeai.ExecuTorchLlamaEngine)?.reinitialize()
                                                }
                                                modelState = modelManager.getModelState()
                                                hardwareSummary = modelManager.getHardwareSummary()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                                    shape = GxShapeSm,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📥 Download On-Device Model (1.08 GB)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Manual file import options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { modelPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt, contentColor = GxTextHi),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import .pte", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { tokenizerPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt, contentColor = GxTextHi),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Tokenizer", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 3. Airplane Mode Benchmark Section
                GxCard(
                    backgroundColor = GxSurfaceAlt,
                    borderColor = GxBorder,
                    contentPadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✈️ AIRPLANE MODE BENCHMARK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxTextHi
                            )
                            Surface(
                                color = if (isModelReady) GxSafeSoft else GxWarningSoft,
                                shape = GxShapePill,
                                border = BorderStroke(0.5.dp, if (isModelReady) GxSafe else GxWarning)
                            ) {
                                Text(
                                    if (isModelReady) "ARM NATIVE READY" else "PROVISION REQUIRED",
                                    color = if (isModelReady) GxSafe else GxWarning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            "Tests raw on-device ExecuTorch inference on Arm cores with airplane mode enabled (no cloud, no internet).",
                            fontSize = 11.sp,
                            color = GxTextLo,
                            lineHeight = 14.sp
                        )

                        OutlinedTextField(
                            value = benchmarkPrompt,
                            onValueChange = { benchmarkPrompt = it },
                            label = { Text("Test Threat Dialogue", color = GxTextLo) },
                            maxLines = 3,
                            colors = customGxTextFieldColors(),
                            shape = GxShapeSm,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                isRunningBenchmark = true
                                scope.launch {
                                    val engine = com.susagi.intelligence.UnifiedThreatEngine.getInstance(context).llamaEngine
                                    val res = (engine as? com.susagi.edgeai.ExecuTorchLlamaEngine)?.generateRawInference(benchmarkPrompt)
                                        ?: com.susagi.edgeai.NativeInferenceBenchmark(
                                            prompt = benchmarkPrompt,
                                            generatedText = "",
                                            timeToFirstTokenMs = 0L,
                                            totalLatencyMs = 0L,
                                            tokensPerSecond = 0f,
                                            isModelLoaded = false,
                                            analysisSource = "ENGINE_UNAVAILABLE"
                                        )
                                    benchmarkResult = res
                                    isRunningBenchmark = false
                                }
                            },
                            enabled = !isRunningBenchmark,
                            colors = ButtonDefaults.buttonColors(containerColor = GxSafe),
                            shape = GxShapeSm,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRunningBenchmark) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    Text("Running On-Device Inference...", fontSize = 11.sp)
                                }
                            } else {
                                Text("⚡ Run Airplane Mode Inference Benchmark", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        if (benchmarkResult != null) {
                            val bench = benchmarkResult!!
                            GxCard(
                                backgroundColor = GxSurface,
                                borderColor = if (bench.isModelLoaded) GxSafe else GxWarning,
                                contentPadding = 10.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Source:", fontSize = 10.sp, color = GxTextLo)
                                        Text(bench.analysisSource, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GxPrimary)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tokens/sec:", fontSize = 10.sp, color = GxTextLo)
                                        Text(String.format("%.1f tok/s", bench.tokensPerSecond), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GxSafe)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Time to First Token (TTFT):", fontSize = 10.sp, color = GxTextLo)
                                        Text("${bench.timeToFirstTokenMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Latency:", fontSize = 10.sp, color = GxTextLo)
                                        Text("${bench.totalLatencyMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                                    }
                                    if (bench.error != null) {
                                        Text("Notice: ${bench.error}", fontSize = 10.sp, color = GxDanger)
                                    }
                                    if (bench.generatedText.isNotBlank()) {
                                        HorizontalDivider(color = GxBorder, modifier = Modifier.padding(vertical = 4.dp))
                                        Text("GENERATED TOKENS:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GxTextLo)
                                        Text(bench.generatedText, fontSize = 11.sp, color = GxTextHi, lineHeight = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = "Close",
                onClick = onDismiss,
                height = 42.dp
            )
        }
    )
}

@Composable
fun WhisperModelProvisioningModal(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whisperManager = remember { com.susagi.stt.whisper.WhisperModelManager(context) }
    val whisperEngine = remember { com.susagi.stt.whisper.WhisperSttEngine.getInstance(context) }

    var isReady by remember { mutableStateOf(whisperManager.isReady()) }
    var downloadProgress by remember { mutableStateOf<com.susagi.stt.whisper.WhisperModelManager.DownloadProgress?>(null) }
    var isDownloading by remember { mutableStateOf(false) }

    var selectedLanguage by remember { mutableStateOf("hi") }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<com.susagi.stt.whisper.WhisperInferenceResult?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = whisperManager.importFromUri(uri, com.susagi.stt.whisper.WhisperModelManager.DEFAULT_MODEL_NAME)
                if (res.isSuccess) {
                    whisperEngine.load()
                    isReady = whisperManager.isReady()
                }
            }
        }
    }

    val preprocessorPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = whisperManager.importFromUri(uri, com.susagi.stt.whisper.WhisperModelManager.DEFAULT_PREPROCESSOR_NAME)
                if (res.isSuccess) {
                    whisperEngine.load()
                    isReady = whisperManager.isReady()
                }
            }
        }
    }

    val tokenizerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = whisperManager.importFromUri(uri, com.susagi.stt.whisper.WhisperModelManager.DEFAULT_TOKENIZER_NAME)
                if (res.isSuccess) {
                    whisperEngine.load()
                    isReady = whisperManager.isReady()
                }
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GxSurface,
        titleContentColor = GxTextHi,
        textContentColor = GxTextMid,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = GxSafe,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Offline Whisper Speech AI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextHi
                    )
                    Text(
                        "Arm Bharat AI-SoC Challenge PS3 · On-Device Audio Intelligence",
                        fontSize = 10.sp,
                        color = GxTextLo
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Hardware & Storage Architecture Card
                GxCard(
                    backgroundColor = GxSurfaceAlt,
                    borderColor = GxBorder,
                    contentPadding = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("CPU Architecture:", fontSize = 11.sp, color = GxTextLo)
                            Text(
                                "Arm64-v8a / v9a (NEON / XNNPACK)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxSafe
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mobile Free Storage:", fontSize = 11.sp, color = GxTextLo)
                            val freeMb = whisperManager.getFreeStorageBytes() / (1024 * 1024)
                            Text(
                                if (freeMb > 1024) String.format("%.1f GB Free", freeMb / 1024f) else "$freeMb MB Free",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxTextHi
                            )
                        }
                        HorizontalDivider(color = GxBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Whisper Model (.pte):", fontSize = 11.sp, color = GxTextLo)
                            val mFile = whisperManager.modelFile
                            Text(
                                if (mFile.exists() && mFile.length() > 30_000_000L) "${mFile.name} (${mFile.length() / (1024 * 1024)} MB)" else "Not Downloaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mFile.exists() && mFile.length() > 30_000_000L) GxSafe else GxDanger
                            )
                        }
                        val mFile = whisperManager.modelFile
                        if (mFile.exists() && mFile.length() > 30_000_000L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Model SHA-256:", fontSize = 10.sp, color = GxTextLo)
                                val hash = remember(mFile.length()) {
                                    runCatching {
                                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                                        mFile.inputStream().use { fis ->
                                            val b = ByteArray(65536)
                                            var n: Int
                                            while (fis.read(b).also { n = it } != -1) {
                                                digest.update(b, 0, n)
                                            }
                                        }
                                        digest.digest().joinToString("") { "%02x".format(it) }
                                    }.getOrDefault("CALCULATING")
                                }
                                Text(
                                    if (hash.length > 20) "${hash.take(10)}...${hash.takeLast(6)}" else hash,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = GxSafe
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Preprocessor (.pte):", fontSize = 11.sp, color = GxTextLo)
                            val pFile = whisperManager.preprocessorFile
                            Text(
                                if (pFile.exists() && pFile.length() > 10_000L) "${pFile.name} (${pFile.length() / 1024} KB)" else "Not Downloaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pFile.exists() && pFile.length() > 10_000L) GxSafe else GxDanger
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tokenizer (.json):", fontSize = 11.sp, color = GxTextLo)
                            val tFile = whisperManager.tokenizerFile
                            Text(
                                if (tFile.exists() && tFile.length() > 1024L) "${tFile.name} (${tFile.length() / 1024} KB)" else "Not Downloaded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tFile.exists() && tFile.length() > 1024L) GxSafe else GxDanger
                            )
                        }
                    }
                }

                // 2. Direct-to-Phone Download Card
                if (!isReady) {
                    GxCard(
                        backgroundColor = GxSafeSoft.copy(alpha = 0.5f),
                        borderColor = GxSafe.copy(alpha = 0.4f),
                        contentPadding = 12.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "PROVISION ON-DEVICE WHISPER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxSafe
                            )
                            Text(
                                "Download quantized Whisper Small INT8 (416 MB total) directly to your mobile storage for offline speech recognition.",
                                fontSize = 11.sp,
                                color = GxTextMid,
                                lineHeight = 15.sp
                            )

                            if (isDownloading) {
                                val p = downloadProgress
                                if (p != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        LinearProgressIndicator(
                                            progress = { (p.percent / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = GxSafe,
                                            trackColor = GxSurfaceAlt
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "${p.percent.toInt()}% (${p.bytesDownloaded / (1024 * 1024)}MB / ${p.totalBytes / (1024 * 1024)}MB)",
                                                fontSize = 10.sp,
                                                color = GxTextHi
                                            )
                                            Text(
                                                String.format("%.1f MB/s", p.speedMbPerSec),
                                                fontSize = 10.sp,
                                                color = GxSafe
                                            )
                                        }
                                        Text(
                                            "Downloading: ${p.currentFileName}",
                                            fontSize = 9.sp,
                                            color = GxTextLo
                                        )
                                        if (p.error != null) {
                                            Text("Error: ${p.error}", fontSize = 10.sp, color = GxDanger)
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GxSafe, strokeWidth = 2.dp)
                                        Text("Connecting to model distribution...", fontSize = 11.sp, color = GxTextMid)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        scope.launch {
                                            val res = whisperManager.downloadWhisperAssets { progress ->
                                                downloadProgress = progress
                                            }
                                            isDownloading = false
                                            if (res.isSuccess) {
                                                whisperEngine.load()
                                                isReady = whisperManager.isReady()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSafe),
                                    shape = GxShapeSm,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("📥 Download On-Device Whisper Small (416 MB)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GxVoid)
                                }
                            }

                            // Manual file import options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { modelPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt, contentColor = GxTextHi),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import .pte", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { preprocessorPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt, contentColor = GxTextHi),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Prep", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { tokenizerPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt, contentColor = GxTextHi),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import Tok", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // 3. Standalone Voice Recorder & Airplane Mode Benchmark
                GxCard(
                    backgroundColor = GxSurfaceAlt,
                    borderColor = GxBorder,
                    contentPadding = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✈️ STANDALONE MIC TEST (OFFLINE)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxTextHi
                            )
                            Surface(
                                color = if (isReady) GxSafeSoft else GxWarningSoft,
                                shape = GxShapePill
                            ) {
                                Text(
                                    if (isReady) "READY" else "UNINITIALIZED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isReady) GxSafe else GxWarning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            "Record a 3-second live voice sample from the microphone in Airplane Mode to verify real on-device Whisper inference.",
                            fontSize = 11.sp,
                            color = GxTextMid,
                            lineHeight = 15.sp
                        )

                        // Language Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Language:", fontSize = 11.sp, color = GxTextLo)
                            Button(
                                onClick = { selectedLanguage = "hi" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedLanguage == "hi") GxSafe else GxSurface,
                                    contentColor = if (selectedLanguage == "hi") GxVoid else GxTextHi
                                ),
                                shape = GxShapeSm,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Hindi (हिन्दी)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { selectedLanguage = "en" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedLanguage == "en") GxSafe else GxSurface,
                                    contentColor = if (selectedLanguage == "en") GxVoid else GxTextHi
                                ),
                                shape = GxShapeSm,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("English", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isRecording) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GxDanger, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Recording 3s voice sample from phone mic...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GxDanger
                                )
                            }
                        } else if (isTranscribing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GxSafe, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Transcribing on Arm ExecuTorch runtime...",
                                    fontSize = 12.sp,
                                    color = GxSafe,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            testError = null
                                            testResult = null
                                            isRecording = true

                                            // Record 3s of 16kHz 16-bit mono PCM (96,000 bytes)
                                            val sampleRate = 16000
                                            val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
                                            val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
                                            val minBuf = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                                            val bufSize = maxOf(minBuf, 4096)

                                            val recorder = android.media.AudioRecord(
                                                android.media.MediaRecorder.AudioSource.MIC,
                                                sampleRate,
                                                channelConfig,
                                                audioFormat,
                                                bufSize
                                            )

                                            val totalBytesToRead = sampleRate * 2 * 3 // 3 seconds
                                            val byteStream = java.io.ByteArrayOutputStream(totalBytesToRead)
                                            val buf = ByteArray(bufSize)

                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    recorder.startRecording()
                                                    var readTotal = 0
                                                    while (readTotal < totalBytesToRead) {
                                                        val n = recorder.read(buf, 0, minOf(buf.size, totalBytesToRead - readTotal))
                                                        if (n > 0) {
                                                            byteStream.write(buf, 0, n)
                                                            readTotal += n
                                                        } else if (n < 0) break
                                                    }
                                                } finally {
                                                    try { recorder.stop() } catch (_: Exception) {}
                                                    try { recorder.release() } catch (_: Exception) {}
                                                }
                                            }

                                            isRecording = false
                                            isTranscribing = true

                                            val pcmBytes = byteStream.toByteArray()
                                            val res = whisperEngine.transcribePcm(
                                                pcmBytes = pcmBytes,
                                                sampleRate = 16000,
                                                language = selectedLanguage
                                            )
                                            testResult = res
                                            if (!res.isSuccess) {
                                                testError = res.errorMessage
                                            }
                                        } catch (t: Throwable) {
                                            testError = t.message
                                        } finally {
                                            isRecording = false
                                            isTranscribing = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                                shape = GxShapeSm,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("🎙️ Record & Test 3s Voice Sample", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Benchmark Results Display
                        val res = testResult
                        if (res != null) {
                            Surface(
                                color = GxSurface,
                                shape = GxShapeSm,
                                border = BorderStroke(1.dp, if (res.isSuccess) GxSafe.copy(alpha = 0.5f) else GxDanger.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Execution Status:", fontSize = 10.sp, color = GxTextLo)
                                        Text(
                                            if (res.isSuccess) "VERIFIED ON-DEVICE (EXECUTORCH ARM)" else "INFERENCE FAILED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.isSuccess) GxSafe else GxDanger
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Inference Latency:", fontSize = 10.sp, color = GxTextLo)
                                        Text("${res.latencyMs} ms", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GxTextHi)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Real-Time Factor (RTF):", fontSize = 10.sp, color = GxTextLo)
                                        Text(
                                            String.format("%.2fx (Audio: %d ms)", res.realTimeFactor, res.audioDurationMs),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GxSafe
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tokens / Sec:", fontSize = 10.sp, color = GxTextLo)
                                        Text(
                                            String.format("%.1f tok/s (%d tokens)", res.tokensPerSecond, res.tokensGenerated),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GxSafe
                                        )
                                    }
                                    HorizontalDivider(color = GxBorder, modifier = Modifier.padding(vertical = 4.dp))
                                    Text("RECOGNIZED SPEECH TRANSCRIPT:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GxTextLo)
                                    Text(
                                        if (res.text.isNotBlank()) "\"${res.text}\"" else "(No vocal speech detected)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GxTextHi,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        if (testError != null) {
                            Text("Notice: $testError", fontSize = 10.sp, color = GxDanger)
                        }
                    }
                }
            }
        },
        confirmButton = {
            GxButton.Primary(
                text = "Close",
                onClick = onDismiss,
                height = 42.dp
            )
        }
    )
}