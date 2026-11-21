import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(localFile.inputStream())
    }
}

android {
    namespace = "com.guardian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.susagi.guardian2"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"

        val geminiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")

        val geminiModel = localProperties.getProperty("GEMINI_MODEL")?.ifBlank { "gemini-1.5-flash" } ?: "gemini-1.5-flash"
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")

        val bhashiniStreamingUrl = localProperties.getProperty("BHASHINI_STREAMING_URL") ?: ""
        buildConfigField("String", "BHASHINI_STREAMING_URL", "\"$bhashiniStreamingUrl\"")

        val agoraAppId = localProperties.getProperty("AGORA_APP_ID") ?: System.getenv("AGORA_APP_ID") ?: ""
        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")

        val bhashiniUserId = localProperties.getProperty("BHASHINI_USER_ID") ?: ""
        buildConfigField("String", "BHASHINI_USER_ID", "\"$bhashiniUserId\"")

        val bhashiniUlcaKey = localProperties.getProperty("BHASHINI_ULCA_API_KEY") ?: ""
        buildConfigField("String", "BHASHINI_ULCA_API_KEY", "\"$bhashiniUlcaKey\"")

        val bhashiniInferenceKey = localProperties.getProperty("BHASHINI_INFERENCE_API_KEY") ?: ""
        buildConfigField("String", "BHASHINI_INFERENCE_API_KEY", "\"$bhashiniInferenceKey\"")

        val bhashiniPipelineId = localProperties.getProperty("BHASHINI_PIPELINE_ID") ?: ""
        buildConfigField("String", "BHASHINI_PIPELINE_ID", "\"$bhashiniPipelineId\"")

        val bhashiniSttEndpoint = localProperties.getProperty("BHASHINI_STT_ENDPOINT") ?: ""
        buildConfigField("String", "BHASHINI_STT_ENDPOINT", "\"$bhashiniSttEndpoint\"")

        val bhashiniRestEndpoint = localProperties.getProperty("BHASHINI_REST_ENDPOINT") ?: ""
        buildConfigField("String", "BHASHINI_REST_ENDPOINT", "\"$bhashiniRestEndpoint\"")

        val bhashiniTransport = localProperties.getProperty("BHASHINI_TRANSPORT") ?: "B"
        buildConfigField("String", "BHASHINI_TRANSPORT", "\"$bhashiniTransport\"")

        val ablyApiKey = localProperties.getProperty("ABLY_API_KEY") ?: ""
        buildConfigField("String", "ABLY_API_KEY", "\"$ablyApiKey\"")

        val backendUrl = localProperties.getProperty("BACKEND_URL") ?: "http://192.168.29.62:3001"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropsFile.exists()) {
            load(keystorePropsFile.inputStream())
        }
    }

    signingConfigs {
        create("release") {
            val keyStorePath = keystoreProperties.getProperty("KEYSTORE_PATH") ?: System.getenv("RELEASE_KEYSTORE_PATH")
            if (keyStorePath != null && file(keyStorePath).exists()) {
                storeFile = file(keyStorePath)
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: ""
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: ""
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD") ?: ""
            } else {
                logger.warn("NOTICE: Production release keystore unconfigured. Falling back to debug keystore. Artifact is classified as DEVELOPMENT-ONLY.")
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")

            // Strict Client Hardening: Prohibit embedding unrestricted cloud API keys into release APKs.
            // Secret-dependent requests (Gemini AI, Bhashini STT) are brokered through the secure backend proxy (BACKEND_URL).
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "BHASHINI_ULCA_API_KEY", "\"\"")
            buildConfigField("String", "BHASHINI_INFERENCE_API_KEY", "\"\"")
            buildConfigField("String", "ABLY_API_KEY", "\"\"")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.addAll(listOf(
                "**/libaosl.so",
                "**/libc++_shared.so",
                "lib/**/libaosl.so",
                "lib/arm64-v8a/libaosl.so",
                "lib/armeabi-v7a/libaosl.so",
                "lib/x86/libaosl.so",
                "lib/x86_64/libaosl.so"
            ))
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Agora Voice & Audio RTC SDK
    implementation("io.agora.rtc:voice-sdk:4.4.1")
    // Agora RTM (Signaling) SDK for Real-Time Transcripts
    implementation("io.agora.rtm:rtm-sdk:2.2.1")

    // CameraX & ML Kit for QR / Barcode Scanner
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Encrypted Session Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room Database & Coroutines for Number Reputation & Call History
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // OkHttp for Bhashini Streaming STT WebSocket & REST Translation/TTS
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "com.squareup.okhttp3")
    }

    // Firebase Cloud Messaging & Auth
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.guava:guava:33.2.1-android")

    // Ably Realtime & Push SDK
    implementation("io.ably:ably-android:1.2.0")

    // ExecuTorch Android Runtime for On-Device Llama Inference (Arm XNNPACK accelerated)
    implementation("org.pytorch:executorch-android:1.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}