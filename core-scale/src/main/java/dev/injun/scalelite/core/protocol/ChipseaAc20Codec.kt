package dev.injun.scalelite.core.protocol

/**
 * Chipsea 20-byte frame, as sent on FFB2 by Active Era BS-06 and Atflee T6_AF502:
 *
 * ```
 * [0]      0xAC   magic
 * [1]      0x17   fixed
 * [2]      0x00   fixed (a stability flag on some firmwares; constant here)
 * [3]      flags in the top 6 bits, weight bits 17..16 in the low 2
 * [4..5]   weight bits 15..0
 * [6..16]  body-composition slots, zero on weight-only models
 * [17]     0x03   fixed
 * [18]     frame type: 0xD5 weight, 0xD6 impedance
 * [19]     checksum = sum(bytes[3..18]) & 0x1F
 * ```
 *
 * Weight is `be24(bytes[3..5]) & 0x3FFFF` grams. The 18-bit width was derived from a
 * ramp capture: byte[3] flipped 0x60 -> 0x61 between 53.54 kg and 67.44 kg, which is
 * exactly where bit 16 (65.536 kg) turns on. 17/18/20-bit masks cannot be told apart
 * below 131 kg, so the widest safe choice is used to avoid clipping heavy users.
 *
 * The protocol has no stability bit: callers confirm a weight by repetition
 * (see [StableWeightDetector]).
 */
object ChipseaAc20Codec : FrameCodec {
    override val dialect: Dialect = Dialect.CHIPSEA_AC20

    const val FRAME_LENGTH = 20
    private const val MAGIC = 0xAC
    private const val TYPE_WEIGHT = 0xD5
    private const val TYPE_IMPEDANCE = 0xD6
    private const val WEIGHT_MASK = 0x3FFFF
    private const val CHECKSUM_MASK = 0x1F

    override fun decode(frame: ByteArray): ScaleFrame? {
        if (frame.size != FRAME_LENGTH || frame.u8(0) != MAGIC) return null
        if (!checksumMatches(frame)) return null
        return when (frame.u8(18)) {
            TYPE_WEIGHT -> ScaleFrame.Weight(grams = weightGrams(frame))
            TYPE_IMPEDANCE -> ScaleFrame.Impedance(ohms = (frame.u8(4) shl 8) or frame.u8(5))
            else -> null
        }
    }

    /** True when bytes[19] carries the 5-bit sum of bytes[3..18]. */
    fun checksumMatches(frame: ByteArray): Boolean {
        if (frame.size != FRAME_LENGTH) return false
        var sum = 0
        for (i in 3..18) sum += frame.u8(i)
        return (sum and CHECKSUM_MASK) == (frame.u8(19) and CHECKSUM_MASK)
    }

    private fun weightGrams(frame: ByteArray): Int {
        val raw = (frame.u8(3) shl 16) or (frame.u8(4) shl 8) or frame.u8(5)
        return raw and WEIGHT_MASK
    }

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
