package dev.injun.scalelite.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StableWeightDetectorTest {

    @Test
    fun `confirms the captured session on the fifth identical frame`() {
        val detector = StableWeightDetector()
        val results = Fixtures.settling.map { frame ->
            detector.offer((ChipseaAc20Codec.decode(frame) as ScaleFrame.Weight).grams)
        }
        assertEquals(listOf(null, null, null, null, null, null, 69_100), results)
    }

    @Test
    fun `a ramp never confirms because no value repeats`() {
        val detector = StableWeightDetector()
        for ((frame, _) in Fixtures.ramp) {
            assertNull(detector.offer((ChipseaAc20Codec.decode(frame) as ScaleFrame.Weight).grams))
        }
    }

    @Test
    fun `readings under the minimum reset the run`() {
        val detector = StableWeightDetector(requiredRepeats = 3)
        detector.offer(70_000)
        detector.offer(70_000)
        detector.offer(0)
        detector.offer(70_000)
        detector.offer(70_000)
        assertNull(detector.offer(70_100))
        assertEquals(1, detector.progress)
    }

    @Test
    fun `confirms once per settle and re-arms after stepping off`() {
        val detector = StableWeightDetector(requiredRepeats = 2)
        assertNull(detector.offer(70_000))
        assertEquals(70_000, detector.offer(70_000))
        assertNull(detector.offer(70_000))
        assertNull(detector.offer(70_000))
        assertNull(detector.offer(500))
        assertNull(detector.offer(80_000))
        assertEquals(80_000, detector.offer(80_000))
    }

    @Test
    fun `progress counts consecutive repeats up to the requirement`() {
        val detector = StableWeightDetector(requiredRepeats = 3)
        detector.offer(70_000)
        assertEquals(1, detector.progress)
        detector.offer(70_000)
        assertEquals(2, detector.progress)
        detector.offer(70_000)
        detector.offer(70_000)
        assertEquals(3, detector.progress)
    }
}
