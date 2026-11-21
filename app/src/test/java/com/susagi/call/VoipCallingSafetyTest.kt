package com.susagi.call

import com.guardian.app.voip.RoomCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
