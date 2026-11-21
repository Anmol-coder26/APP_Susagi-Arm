package com.guardian.app.core

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import com.guardian.app.BuildConfig

/**
 * FIXES: "RTC Engine unavailable" — caused by duplicate RtcEngine.create() calls,
 * usually from calling create() in more than one Activity/Service/ViewModel.
 *
 * RULE: There must be exactly ONE RtcEngine instance for the lifetime of the call
 * feature. Never call RtcEngine.create() or RtcEngine.destroy() from anywhere except
 * this object.
 *
 * Double-checked locking: cheap on the common path (engine already exists),
 * safe under concurrent access.
 */
object AgoraEngineManager {

    @Volatile private var engine: RtcEngine? = null
    private val lock = Any()

    /**
     * Double-checked locking: cheap on the common path (engine already exists),
     * safe under concurrent access.
     */
    fun get(
        context: Context,
        appId: String = BuildConfig.AGORA_APP_ID.ifBlank { "d575bd8b35004ad896366419f3a8a8f1" },
        eventHandler: IRtcEngineEventHandler? = null
    ): RtcEngine {
        val current = engine
        if (current != null) {
            if (eventHandler != null) {
                try { current.addHandler(eventHandler) } catch (_: Exception) {}
            }
            return current
        }
        synchronized(lock) {
            val syncCurrent = engine
            if (syncCurrent != null) {
                if (eventHandler != null) {
                    try { syncCurrent.addHandler(eventHandler) } catch (_: Exception) {}
                }
                return syncCurrent
            }
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                if (eventHandler != null) {
                    mEventHandler = eventHandler
                }
            }
            val created = RtcEngine.create(config)
            engine = created
            Log.i("AgoraEngineManager", "RtcEngine created with double-checked locking")
            return created
        }
    }

    /** Returns null instead of creating — use this when you just need to check state. */
    fun peek(): RtcEngine? = engine

    fun isInitialized(): Boolean = engine != null

    /**
     * Call ONLY on process death or an explicit "user disabled call protection" action.
     * Do NOT call this in onDestroy() of the call Activity/Fragment, and do NOT call
     * it when a single call ends — leaveChannel() is what ends a call, not destroy().
     */
    fun destroy() {
        synchronized(lock) {
            if (engine != null) {
                try {
                    RtcEngine.destroy()
                } catch (e: Exception) {
                    Log.e("AgoraEngineManager", "Error destroying RtcEngine: ${e.message}")
                }
                engine = null
                Log.i("AgoraEngineManager", "RtcEngine destroyed")
            }
        }
    }
}
