package dev.injun.scalelite.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChipseaAc20CodecTest {

    private val codec = ChipseaAc20Codec

    @Test
    fun `decodes every captured ramp frame to the expected grams`() {
        for ((frame, grams) in Fixtures.ramp) {
            assertEquals(ScaleFrame.Weight(grams), codec.decode(frame), frame.toHex())
        }
    }

    @Test
    fun `weight bit 16 lives in the low bits of byte 3`() {
        val below = Fixtures.ramp[3]
        val above = Fixtures.ramp[4]
        assertEquals(0x60, below.first[3].toInt() and 0xFF)
        assertEquals(0x61, above.first[3].toInt() and 0xFF)
        assertTrue(below.second < 65_536 && above.second > 65_536)
    }

    @Test
    fun `ramp decodes monotonically until the settle point`() {
        val grams = Fixtures.ramp.take(6).map { (codec.decode(it.first) as ScaleFrame.Weight).grams }
        assertEquals(grams.sorted(), grams)
    }

    @Test
    fun `rejects a frame whose checksum does not match`() {
        val frame = Fixtures.ramp[5].first.copyOf().also { it[4] = (it[4] + 1).toByte() }
        assertFalse(codec.checksumMatches(frame))
        assertNull(codec.decode(frame))
    }

    @Test
    fun `rejects wrong length and wrong magic`() {
        assertNull(codec.decode(Fixtures.ramp[0].first.copyOf(19)))
        assertNull(codec.decode(Fixtures.ramp[0].first.copyOf().also { it[0] = 0x02 }))
    }

    @Test
    fun `decodes an impedance frame`() {
        // Same layout, type 0xD6, ohms 0x01F4 = 500 in bytes 4..5; checksum recomputed.
        val frame = Fixtures.hex("AC 17 00 60 01 F4 00 00 00 00 00 00 00 00 00 00 00 03 D6 00")
        frame[19] = ((3..18).sumOf { frame[it].toInt() and 0xFF } and 0x1F).toByte()
        assertEquals(ScaleFrame.Impedance(500), codec.decode(frame))
    }

    @Test
    fun `unknown frame type is ignored even with a valid checksum`() {
        val frame = Fixtures.ramp[5].first.copyOf().also { it[18] = 0xD7.toByte() }
        frame[19] = ((3..18).sumOf { frame[it].toInt() and 0xFF } and 0x1F).toByte()
        assertNull(codec.decode(frame))
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
}
