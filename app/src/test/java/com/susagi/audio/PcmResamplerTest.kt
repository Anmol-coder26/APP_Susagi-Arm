package com.susagi.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmResamplerTest {

    private lateinit var resampler: PcmResampler

    @Before
    fun setUp() {
        resampler = PcmResampler(targetSampleRate = 16000)
    }

    @Test
    fun testSameSampleRate_preservesDataDirectly() {
        // 160 samples @ 16kHz = 10ms
        val input = ShortArray(160) { (it * 100).toShort() }
        val output = resampler.resampleShorts(input, inRate = 16000, outRate = 16000)

        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i])
        }
    }

    @Test
    fun test8kHzTo16kHz_upsamplesByFactorOfTwo() {
        // 80 samples @ 8kHz (10ms) -> should produce ~160 samples @ 16kHz
        val input = ShortArray(80) { 1000.toShort() }
        val output = resampler.resampleShorts(input, inRate = 8000, outRate = 16000)

        // Factor of 2
        assertEquals(160, output.size)
        // Values should match the constant input signal
        assertEquals(1000.toShort(), output[0])
        assertEquals(1000.toShort(), output[output.size - 1])
    }

    @Test
    fun test48kHzTo16kHz_downsamplesByFactorOfThree() {
        // 480 samples @ 48kHz (10ms) -> should produce 160 samples @ 16kHz
        val input = ShortArray(480) { 500.toShort() }
        val output = resampler.resampleShorts(input, inRate = 48000, outRate = 16000)

        assertEquals(160, output.size)
        assertEquals(500.toShort(), output[0])
    }

    @Test
    fun test44_1kHzTo16kHz_producesCorrectProportion() {
        // 441 samples @ 44.1kHz (10ms) -> should produce 160 samples @ 16kHz
        val input = ShortArray(441) { 2000.toShort() }
        val output = resampler.resampleShorts(input, inRate = 44100, outRate = 16000)

        assertEquals(160, output.size)
        assertTrue(output.all { it in 1999..2001 })
    }

    @Test
    fun testStereoDownmix_averagesLeftAndRight() {
        // 4 stereo frames: L=1000, R=2000 -> Expected mono = 1500
        val frameCount = 4
        val bytes = ByteArray(frameCount * 2 * 2) // 4 frames * 2 channels * 2 bytes
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            buf.putShort(1000.toShort()) // L
            buf.putShort(2000.toShort()) // R
        }

        val outputBytes = resampler.resample(bytes, offset = 0, length = bytes.size, inputSampleRate = 16000, inputChannels = 2)
        val outBuf = ByteBuffer.wrap(outputBytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(frameCount * 2, outputBytes.size) // 4 mono shorts * 2 bytes = 8 bytes
        for (i in 0 until frameCount) {
            assertEquals(1500.toShort(), outBuf.short)
        }
    }

    @Test
    fun testStatefulInterpolationContinuity_acrossFrameBoundaries() {
        // Continuous sine wave or ramp across two consecutive buffers
        // Frame 1 ends at 1000, Frame 2 starts at 1010
        val frame1 = ShortArray(80) { (it * 10).toShort() }
        val frame2 = ShortArray(80) { (800 + it * 10).toShort() }

        val out1 = resampler.resampleShorts(frame1, inRate = 8000, outRate = 16000)
        val out2 = resampler.resampleShorts(frame2, inRate = 8000, outRate = 16000)

        assertEquals(160, out1.size)
        assertEquals(160, out2.size)

        // Verify boundary continuity: out1.last() and out2.first() should be smooth and contiguous
        val lastVal = out1.last().toInt()
        val nextVal = out2.first().toInt()
        val diff = Math.abs(nextVal - lastVal)

        // The step between samples is ~5 units. Diff should not jump or reset to 0
        assertTrue("Boundary discontinuity detected: last=$lastVal, next=$nextVal, diff=$diff", diff <= 15)
    }
}
