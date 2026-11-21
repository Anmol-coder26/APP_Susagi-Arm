package com.guardian.app.contacts

import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.auth.FirebaseAuth
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class GuardianContact(
    val uid: String? = null,
    val name: String,
    val phoneNumber: String,
    val isOnGuardian: Boolean = false
)

object ContactsManager {
    private val client = OkHttpClient()

    fun getDeviceContacts(context: Context): List<GuardianContact> {
        val list = mutableListOf<GuardianContact>()
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seenNumbers = mutableSetOf<String>()

            while (c.moveToNext()) {
                val name = c.getString(nameIdx) ?: "Unknown"
                val rawNum = c.getString(numIdx) ?: continue
                val normalized = normalizeE164(rawNum)
                if (normalized.isNotBlank() && seenNumbers.add(normalized)) {
                    list.add(GuardianContact(name = name, phoneNumber = normalized, isOnGuardian = false))
                }
            }
        }
        return list
    }

    private fun normalizeE164(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+")) return digits
        if (digits.length == 10) return "+91$digits"
        if (digits.length == 12 && digits.startsWith("91")) return "+$digits"
        return if (digits.isNotBlank()) "+$digits" else ""
    }

    suspend fun lookupGuardianContacts(context: Context, localContacts: List<GuardianContact>): Pair<List<GuardianContact>, List<GuardianContact>> = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
        val tokenResult = try {
            user?.getIdToken(true)?.await()
        } catch (e: Exception) {
            null
        }

        val allNumbers = localContacts.map { it.phoneNumber }.distinct()
        if (allNumbers.isEmpty()) return@withContext Pair(emptyList(), emptyList())

        val backendUrl = BuildConfig.BACKEND_URL + "/users/lookup"
        val payload = JSONObject().apply {
            put("phoneNumbers", JSONArray(allNumbers))
        }

        val registeredMap = mutableMapOf<String, String>() // phone -> uid

        try {
            val reqBuilder = Request.Builder()
                .url(backendUrl)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))

            tokenResult?.token?.let { reqBuilder.header("Authorization", "Bearer $it") }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("registeredUsers") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val p = obj.optString("phoneNumber")
                        val u = obj.optString("uid")
                        if (p.isNotBlank()) registeredMap[p] = u
                    }
                    CallDiagnostics.log("SIG", "POST /users/lookup success: ${registeredMap.size} Guardian users found out of ${allNumbers.size} contacts")
                } else {
                    CallDiagnostics.log("SIG", "POST /users/lookup HTTP ${resp.code}", LogLevel.WARN)
                }
            }
        } catch (e: Exception) {
            CallDiagnostics.log("SIG", "POST /users/lookup error: ${e.message}", LogLevel.WARN)
        }

        val onGuardian = mutableListOf<GuardianContact>()
        val inviteList = mutableListOf<GuardianContact>()

        for (contact in localContacts) {
            val uid = registeredMap[contact.phoneNumber]
            if (uid != null) {
                onGuardian.add(contact.copy(uid = uid, isOnGuardian = true))
            } else {
                inviteList.add(contact)
            }
        }

        Pair(onGuardian, inviteList)
    }
}
