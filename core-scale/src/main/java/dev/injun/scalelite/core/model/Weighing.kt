package dev.injun.scalelite.core.model

/** A confirmed weigh-in: the scale settled on [grams] at [epochMillis]. */
data class Weighing(
    val grams: Int,
    val epochMillis: Long,
    /** The raw frame the value was decoded from, kept for diagnostics. */
    val rawFrame: ByteArray,
) {
    val kilograms: Double get() = grams / 1000.0

    override fun equals(other: Any?): Boolean =
        other is Weighing && grams == other.grams && epochMillis == other.epochMillis &&
            rawFrame.contentEquals(other.rawFrame)

    override fun hashCode(): Int = 31 * (31 * grams + epochMillis.hashCode()) + rawFrame.contentHashCode()
}
