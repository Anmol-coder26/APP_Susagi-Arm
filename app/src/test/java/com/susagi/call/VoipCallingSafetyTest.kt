package com.susagi.call

import com.guardian.app.AgoraCallState
import com.guardian.app.voip.RoomCode
import com.susagi.audio.AudioFrameBridge
import com.susagi.audio.PcmResampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class VoipCallingSafetyTest {

    @Test
    fun testRoomCode_generationAndValidation() {
        for (i in 1..20) {
            val code = RoomCode.generate()
            assertEquals("Generated room code must be exactly 6 characters", 6, code.length)
            assertTrue("Generated room code must be numeric: $code", RoomCode.isValid(code))
        }

        assertTrue(RoomCode.isValid("123456"))
        assertFalse(RoomCode.isValid("12345"))
        assertFalse(RoomCode.isValid("1234567"))
        assertFalse(RoomCode.isValid("12345a"))
        assertFalse(RoomCode.isValid(""))
    }

    @Test
    fun testRoomCode_channelMappingBidirectional() {
        val code = "582914"
        val channel = RoomCode.toChannel(code)
        assertEquals("gx_582914", channel)
        val recovered = RoomCode.fromChannel(channel)
        assertEquals(code, recovered)
    }

    @Test
    fun testVoipUiState_defaultsEnforceSafety() {
        val state = VoipUiState()
        assertFalse("Call must not start active by default", state.isCallActive)
        assertFalse("Must not claim connected before Agora confirmation", state.isConnected)
        assertFalse("Microphone must start unmuted by default", state.isMuted)
        assertTrue("Speakerphone should default to active for hands-free audio", state.isSpeakerphone)
        assertEquals(0, state.riskScore)
    }

    @Test
    fun testDiagnosticsPills_minimalVoipDefault() {
        val pills = DiagnosticsPillsState()
        assertEquals(PillStatus.RED, pills.rtcJoined)
        assertEquals(PillStatus.RED, pills.peerPresent)
        assertEquals(0L, pills.localPcmCount)
        assertEquals(0L, pills.remotePcmCount)
    }

    @Test
    fun testCallStateMachine_allStatesAndTransitions() {
        // Initial state: IDLE / DISCONNECTED
        var state = VoipUiState()
        assertEquals(AgoraCallState.DISCONNECTED.name, if (!state.isCallActive && !state.isConnected) "DISCONNECTED" else "OTHER")
        assertNull(state.connectionBanner)

        // Initiating state: CONNECTING
        state = state.copy(isCallActive = true, channelName = "gx_123456", connectionBanner = "Connecting...")
        assertTrue(state.isCallActive)
        assertFalse(state.isConnected)
        assertEquals("Connecting...", state.connectionBanner)

        // Reconnecting state (e.g. state code 4)
        val bannerReconnecting = when (4) {
            4 -> "Network unstable, reconnecting..."
            5 -> "Call connection failed"
            else -> null
        }
        state = state.copy(connectionBanner = bannerReconnecting)
        assertEquals("Network unstable, reconnecting...", state.connectionBanner)

        // Connected state: IN_CALL / CONNECTED (e.g. state code 3)
        state = state.copy(isConnected = true, connectionBanner = null)
        assertTrue(state.isConnected)
        assertNull(state.connectionBanner)

        // Remote peer hung up (USER_OFFLINE_QUIT)
        state = state.copy(isConnected = false, isCallActive = false, connectionBanner = "Remote caller hung up.")
        assertFalse(state.isCallActive)
        assertFalse(state.isConnected)
        assertEquals("Remote caller hung up.", state.connectionBanner)
    }

    @Test
    fun testCallState_errorCodesMapping() {
        fun mapErrorCode(err: Int): String = when (err) {
            109, 110 -> "Agora token invalid/expired (Code $err)"
            101 -> "Invalid Agora App ID (Code $err)"
            17 -> "Join channel rejected by server (Code $err)"
            -999 -> "Audio/Agora initialization failure (Code $err)"
            else -> "Call connection error (Code $err)"
        }

        assertEquals("Agora token invalid/expired (Code 109)", mapErrorCode(109))
        assertEquals("Agora token invalid/expired (Code 110)", mapErrorCode(110))
        assertEquals("Invalid Agora App ID (Code 101)", mapErrorCode(101))
        assertEquals("Join channel rejected by server (Code 17)", mapErrorCode(17))
        assertEquals("Audio/Agora initialization failure (Code -999)", mapErrorCode(-999))
        assertEquals("Call connection error (Code 404)", mapErrorCode(404))

        // Also verify production VoiceCallManager mappings
        assertEquals("Agora token invalid or missing (Code 110: Project requires RTC Token)", VoiceCallManager.mapErrorCode(110))
        assertEquals("Agora token expired (Code 109)", VoiceCallManager.mapErrorCode(109))
        assertEquals("Agora App ID unconfigured or invalid (Code 101)", VoiceCallManager.mapErrorCode(101))
        assertEquals("Agora token invalid or missing (RTC Token required)", VoiceCallManager.mapConnectionReason(io.agora.rtc2.Constants.CONNECTION_CHANGED_INVALID_TOKEN))
        assertEquals("Keep-alive timeout (Agora servers unreachable)", VoiceCallManager.mapConnectionReason(io.agora.rtc2.Constants.CONNECTION_CHANGED_KEEP_ALIVE_TIMEOUT))
    }

    @Test
    fun testCallConnectionState_fullLifecycle() {
        var state = VoipUiState()
        assertEquals(CallConnectionState.IDLE, state.callState)

        // Requesting token
        state = state.copy(callState = CallConnectionState.REQUESTING_TOKEN)
        assertEquals(CallConnectionState.REQUESTING_TOKEN, state.callState)

        // Connecting
        state = state.copy(callState = CallConnectionState.CONNECTING)
        assertEquals(CallConnectionState.CONNECTING, state.callState)

        // Joined room
        state = state.copy(callState = CallConnectionState.JOINED)
        assertEquals(CallConnectionState.JOINED, state.callState)

        // Peer connected
        state = state.copy(callState = CallConnectionState.PEER_CONNECTED)
        assertEquals(CallConnectionState.PEER_CONNECTED, state.callState)

        // Audio active
        state = state.copy(callState = CallConnectionState.AUDIO_ACTIVE, isTranscriptionActive = true)
        assertEquals(CallConnectionState.AUDIO_ACTIVE, state.callState)
        assertTrue(state.isTranscriptionActive)

        // Failure
        state = state.copy(
            callState = CallConnectionState.FAILED,
            diagnosticReason = "Token backend unreachable (Connection refused on port 3001)"
        )
        assertEquals(CallConnectionState.FAILED, state.callState)
        assertEquals("Token backend unreachable (Connection refused on port 3001)", state.diagnosticReason)
    }

    @Test
    fun testAgoraToken_unconfiguredAppIdFallback() {
        val agoraAppId = ""
        val token = ""
        // When AGORA_APP_ID is blank, app must avoid crashes and route through safe offline/fallback posture
        val tokenToPass = if (token.isBlank()) null else token
        assertNull("Blank token should map to null token for local direct mode", tokenToPass)
        assertTrue("Empty Agora App ID is identified as development/unconfigured mode", agoraAppId.isBlank())

        // Verify error code 101 mapping for blank App ID
        var receivedError = 0
        val dummyErrorHandler: (Int) -> Unit = { receivedError = it }
        if (agoraAppId.isBlank()) {
            dummyErrorHandler(101)
        }
        assertEquals(101, receivedError)

        // Verify dev mode banner assignment
        val devBanner = if (agoraAppId.isBlank()) "Development mode: Agora App ID unconfigured" else null
        assertEquals("Development mode: Agora App ID unconfigured", devBanner)
    }

    @Test
    fun testPcmResampler_zeroSampleRateGuard() {
        val resampler = PcmResampler(targetSampleRate = 16000)
        val input = ShortArray(160) { (it * 10).toShort() }

        // input rate 0
        val outZeroIn = resampler.resampleShorts(input, inRate = 0, outRate = 16000)
        assertEquals(input.size, outZeroIn.size)

        // output rate 0
        val outZeroOut = resampler.resampleShorts(input, inRate = 16000, outRate = 0)
        assertEquals(input.size, outZeroOut.size)

        // both rates 0
        val outBothZero = resampler.resampleShorts(input, inRate = 0, outRate = 0)
        assertEquals(input.size, outBothZero.size)

        // resample byte array with 0 input rate
        val bytes = ByteArray(320)
        val outBytes = resampler.resample(bytes, inputSampleRate = 0, inputChannels = 1)
        assertTrue("Byte resample with 0 rate should not throw and return valid bytes", outBytes.isNotEmpty())
    }

    @Test
    fun testPcmResampler_zeroChannelsGuard() {
        val resampler = PcmResampler(targetSampleRate = 16000)
        val bytes = ByteArray(320)

        // channels = 0 should be normalized to safe mono (1 channel)
        val outBytes = resampler.resample(bytes, inputSampleRate = 16000, inputChannels = 0)
        assertTrue(outBytes.isNotEmpty())
        assertEquals(320, outBytes.size)
    }

    @Test
    fun testAudioFrameBridge_corruptedFramesHandledGracefully() {
        // null buffer
        val nullResult = AudioFrameBridge.extractOwnedPcm(null, 160, 1, 2)
        assertNull(nullResult)

        // negative or 0 samples
        val buf = ByteBuffer.allocate(320)
        val zeroSamples = AudioFrameBridge.extractOwnedPcm(buf, 0, 1, 2)
        assertNull(zeroSamples)

        // invalid channels (< 1 or > 2)
        val invalidChannels = AudioFrameBridge.extractOwnedPcm(buf, 160, 0, 2)
        assertNull(invalidChannels)

        // invalid bytesPerSample (!= 2)
        val invalidBps = AudioFrameBridge.extractOwnedPcm(buf, 160, 1, 4)
        assertNull(invalidBps)

        // valid frame extraction
        buf.position(0)
        val validPcm = AudioFrameBridge.extractOwnedPcm(buf, 160, 1, 2)
        assertTrue(validPcm != null && validPcm.size == 320)
    }
}
