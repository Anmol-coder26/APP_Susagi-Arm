package com.guardian.app.auth

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UserProfile(
    val uid: String,
    val phoneNumber: String,
    val displayName: String,
    val fcmToken: String
)

object AuthManager {
    private const val PREFS_NAME = "guardian_encrypted_user_prefs"
    private val client = OkHttpClient()

    private fun getPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLocalUser(context: Context, profile: UserProfile) {
        getPrefs(context).edit().apply {
            putString("uid", profile.uid)
            putString("phoneNumber", profile.phoneNumber)
            putString("displayName", profile.displayName)
            putString("fcmToken", profile.fcmToken)
            apply()
        }
    }

    fun getLocalUser(context: Context): UserProfile? {
        val prefs = getPrefs(context)
        val uid = prefs.getString("uid", null) ?: FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val phone = prefs.getString("phoneNumber", null) ?: FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
        val name = prefs.getString("displayName", "Guardian User") ?: "Guardian User"
        val fcm = prefs.getString("fcmToken", "") ?: ""
        return UserProfile(uid, phone, name, fcm)
    }

    fun sendOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onVerificationCompleted: () -> Unit,
        onError: (message: String) -> Unit
    ) {
        CallDiagnostics.log("SIG", "Initiating Phone Auth OTP for $phoneNumber")
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    CallDiagnostics.log("SIG", "Phone Auth instant verification completed")
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onVerificationCompleted()
                            } else {
                                onError(task.exception?.message ?: "Sign-in failed")
                            }
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    CallDiagnostics.log("SIG", "Phone Auth verification failed: ${e.message}", LogLevel.ERROR)
                    onError(e.message ?: "Verification failed")
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    CallDiagnostics.log("SIG", "OTP sent successfully. Verification ID generated.")
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        verificationId: String,
        code: String,
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    CallDiagnostics.log("SIG", "OTP verification successful. User signed in.")
                    onSuccess()
                } else {
                    CallDiagnostics.log("SIG", "OTP code invalid: ${task.exception?.message}", LogLevel.ERROR)
                    onError(task.exception?.message ?: "Invalid OTP code")
                }
            }
    }

    suspend fun registerWithBackend(context: Context, displayName: String = "Guardian User"): Boolean = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext false
        val tokenResult = try {
            user.getIdToken(true).await()
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "Failed to fetch Firebase ID token: ${e.message}", LogLevel.ERROR)
            null
        } ?: return@withContext false

        val fcmToken = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            ""
        }

        val profile = UserProfile(
            uid = user.uid,
            phoneNumber = user.phoneNumber ?: "",
            displayName = displayName,
            fcmToken = fcmToken
        )
        saveLocalUser(context, profile)

        val backendUrl = BuildConfig.BACKEND_URL + "/users/register"
        val payload = JSONObject().apply {
            put("uid", profile.uid)
            put("phoneNumber", profile.phoneNumber)
            put("displayName", profile.displayName)
            put("fcmToken", profile.fcmToken)
            put("platform", "android")
        }

        try {
            val request = Request.Builder()
                .url(backendUrl)
                .header("Authorization", "Bearer ${tokenResult.token}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                CallDiagnostics.log("SIG", "POST /users/register response: ${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "POST /users/register network error: ${e.message}", LogLevel.WARN)
            false
        }
    }
}
