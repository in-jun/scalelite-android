package dev.injun.scalelite.core.protocol

/** One decoded notification from a scale. */
sealed interface ScaleFrame {
    /** A live weight reading. [stable] is null when the dialect carries no stability flag. */
    data class Weight(val grams: Int, val stable: Boolean? = null) : ScaleFrame

    /** Raw bio-impedance in ohms; only scales with electrodes send this. */
    data class Impedance(val ohms: Int) : ScaleFrame
}

/** Decodes the notify payload of one scale dialect. Returns null for frames it does not own. */
interface FrameCodec {
    val dialect: Dialect
    fun decode(frame: ByteArray): ScaleFrame?
}
