package dev.injun.scalelite.core.protocol

/** Frames captured from an Atflee T6_AF502 (Chipsea 20-byte dialect). */
object Fixtures {
    fun hex(s: String): ByteArray = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    /** Stepping on from empty: 0.00 -> 68.52 kg. byte[3] flips 0x60 -> 0x61 past 65.536 kg. */
    val ramp = listOf(
        "AC 17 00 60 00 00 00 00 00 00 00 00 00 00 00 00 00 03 D5 18" to 0,
        "AC 17 00 60 13 D8 00 00 00 00 00 00 00 00 00 00 00 03 D5 03" to 5_080,
        "AC 17 00 60 5E D8 00 00 00 00 00 00 00 00 00 00 00 03 D5 0E" to 24_280,
        "AC 17 00 60 D1 24 00 00 00 00 00 00 00 00 00 00 00 03 D5 0D" to 53_540,
        "AC 17 00 61 07 70 00 00 00 00 00 00 00 00 00 00 00 03 D5 10" to 67_440,
        "AC 17 00 61 0B A8 00 00 00 00 00 00 00 00 00 00 00 03 D5 0C" to 68_520,
        "AC 17 00 61 0A 90 00 00 00 00 00 00 00 00 00 00 00 03 D5 13" to 68_240,
        "AC 17 00 61 0A 4A 00 00 00 00 00 00 00 00 00 00 00 03 D5 0D" to 68_170,
    ).map { (h, g) -> hex(h) to g }

    /** A full successful session: two frames at 69.05 kg, then 69.10 kg repeated. */
    val settling = listOf(
        "AC 17 00 61 0D BA 00 00 00 00 00 00 00 00 00 00 00 03 D5 00",
        "AC 17 00 61 0D BA 00 00 00 00 00 00 00 00 00 00 00 03 D5 00",
        "AC 17 00 61 0D EC 00 00 00 00 00 00 00 00 00 00 00 03 D5 12",
        "AC 17 00 61 0D EC 00 00 00 00 00 00 00 00 00 00 00 03 D5 12",
        "AC 17 00 61 0D EC 00 00 00 00 00 00 00 00 00 00 00 03 D5 12",
        "AC 17 00 61 0D EC 00 00 00 00 00 00 00 00 00 00 00 03 D5 12",
        "AC 17 00 61 0D EC 00 00 00 00 00 00 00 00 00 00 00 03 D5 12",
    ).map(::hex)

    val t6Fingerprint = GattFingerprint(
        services = setOf("1800", "1801", "ffb0"),
        characteristics = mapOf("ffb1" to "ffb0", "ffb2" to "ffb0"),
    )
}
