package com.susagi.browser

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Diagnostic and configuration manager for Android Default Browser Role (`ROLE_BROWSER`).
 *
 * Provides honest platform inspection of whether SuSagi Guardian V2 actually holds the
 * system default browser role, assists the user in setting it, and transparently details
 * OS-level limitations such as Android App Links and in-app WebViews.
 */
object BrowserRoleManager {

    /**
     * Checks whether this app holds the Android system default browser role.
     */
    fun isDefaultBrowser(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        } else {
            val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            val resolveInfo = context.packageManager.resolveActivity(
                testIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    /**
     * Retrieves the package name of the currently active default browser.
     */
    fun getDefaultBrowserPackage(context: Context): String? {
        val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolveInfo = context.packageManager.resolveActivity(
            testIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Requests the default browser role from the user via the system role dialog.
     */
    fun requestDefaultBrowserRole(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                launcher.launch(intent)
                return
            }
        }

        // Fallback to default apps settings screen on pre-Q
        try {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(settingsIntent)
        } catch (_: Exception) {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activity.startActivity(appDetailsIntent)
        }
    }

    /**
     * Returns clear, truthful documentation of platform routing limitations.
     */
    fun getPlatformRoutingExplanation(): String {
        return """
            1. Standard Web Links: When SuSagi holds the default browser role, clicking web links (HTTP/HTTPS) in SMS, WhatsApp, Gmail, or chat apps routes to SuSagi Link Shield before any website loads.
            2. Verified Android App Links: Domains cryptographically claimed by installed applications (e.g., YouTube, specific banking apps) are routed directly into those apps by Android OS.
            3. Embedded In-App WebViews: Some apps (e.g., Instagram, Twitter) render links inside their own private internal WebView rather than dispatching a system intent.
            4. Universal Share Shield: For any link inside an app that bypasses default-browser routing, simply tap "Share" and select "SuSagi Guardian" to inspect the destination offline before interacting.
        """.trimIndent()
    }
}
