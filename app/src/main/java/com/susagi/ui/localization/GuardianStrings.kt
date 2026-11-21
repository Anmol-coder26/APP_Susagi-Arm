package com.susagi.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी")
}

object AppLanguageState {
    var currentLanguage by mutableStateOf(AppLanguage.ENGLISH)

    fun toggleLanguage() {
        currentLanguage = if (currentLanguage == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguageState.currentLanguage }

object Strings {
    // Top Bar & Identity
    val appTitle: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सुसागी गार्जियन" else "SuSagi Guardian"
    val subtitle: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "ऑन-डिवाइस साइबर सुरक्षा" else "On-Device Threat Shield"

    // Shield Hero
    val shieldActive: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सुरक्षा सक्रिय है" else "Shield Active"
    val shieldStandby: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सुरक्षा रुकी हुई है" else "Shield Standby"
    val shieldDescActive: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) 
        "कॉल, संदिग्ध लिंक और जबरन वसूली संदेशों की रीयल-टाइम सुरक्षा।"
        else "Real-time on-device protection screening incoming calls, suspicious links & extortion messages."
    val shieldDescStandby: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI)
        "निगरानी रुकी हुई है। पुनः सक्रिय करने के लिए टैप करें।"
        else "Protection is in standby. Tap to reactivate real-time screening."

    // Calling Lobby
    val secureCall: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सुरक्षित वीओआईपी कॉल" else "Secure VoIP Call"
    val hostRoom: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "कॉल शुरू करें" else "Host Call"
    val enterPin: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "पिन दर्ज करें" else "Enter PIN"
    val roomCodeHint: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "६ अंकों का कोड साझा करें" else "Share this 6-digit room PIN"
    val joinRoom: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "कमरे में शामिल हों" else "Join Room"

    // Active Call
    val inCallWith: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सुरक्षित कॉल जारी" else "Secure Call Active"
    val mute: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "म्यूट" else "Mute"
    val unmute: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "अनम्यूट" else "Unmute"
    val speaker: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "स्पीकर" else "Speaker"
    val earpiece: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "ईयरपीस" else "Earpiece"
    val endCall: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "कॉल समाप्त करें" else "End Call"

    // Tools
    val linkBarrierTitle: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "लिंक सुरक्षा अवरोधक" else "Link Safety Barrier"
    val linkBarrierDesc: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) 
        "ब्राउज़र में खोलने से पहले दुर्भावनापूर्ण फ़िशिंग व एपीके पेलोड की जाँच करें।" 
        else "Verify URLs before opening to prevent phishing traps and malicious APK payloads."
    val messageScanTitle: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "संदेश स्कैनर" else "Message Scanner"
    val messageScanDesc: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI)
        "डिजिटल अरेस्ट, बिजली बिल व लॉटरी घोटालों के लिए एसएमएस का विश्लेषण करें।"
        else "Scan SMS or WhatsApp text for digital arrest coercion and financial fraud patterns."

    // Settings & Permissions
    val settingsTitle: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI) "सेटिंग्स व गोपनीयता" else "Settings & Privacy"
    val notificationDisclaimer: String get() = if (AppLanguageState.currentLanguage == AppLanguage.HINDI)
        "एंड्रॉइड सुरक्षा नीति: ऐप केवल उन सूचनाओं के शीर्षक और विवरण की जाँच करता है जो उपयोगकर्ता द्वारा स्पष्ट अनुमति दी गई हों। यह किसी भी चैट ऐप के एन्क्रिप्टेड डेटाबेस तक नहीं पहुँचता।"
        else "Android OS Sandboxing Notice: SuSagi analyzes readable notification text only after explicit permission. It does not access private chat databases of WhatsApp or other apps."
}
