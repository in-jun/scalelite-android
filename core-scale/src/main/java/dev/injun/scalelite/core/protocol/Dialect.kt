package dev.injun.scalelite.core.protocol

/**
 * A wire protocol spoken over the vendor GATT service. Scales from unrelated brands share
 * a dialect when they ship the same OEM firmware, so identification goes by what the GATT
 * tree looks like rather than by advertised name.
 */
enum class Dialect(val displayName: String) {
    /** Chipsea SoC firmware: 20-byte `0xAC` frames on FFB2 (Active Era BS-06, Atflee T6_AF502). */
    CHIPSEA_AC20("Chipsea 20-byte"),
    ;

    fun codec(): FrameCodec = when (this) {
        CHIPSEA_AC20 -> ChipseaAc20Codec
    }
}

/** 16-bit Bluetooth SIG short UUIDs used across dialects, as lowercase 4-hex strings. */
object GattIds {
    const val SERVICE_FFB0 = "ffb0"
    const val CHAR_FFB1_WRITE = "ffb1"
    const val CHAR_FFB2_NOTIFY = "ffb2"
    const val CHAR_FFB3 = "ffb3"
    const val CCCD = "2902"

    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** Full 128-bit form of a 16-bit id, e.g. `ffb0` -> `0000ffb0-0000-1000-8000-00805f9b34fb`. */
    fun full(short: String): String = "0000${short.lowercase()}$BASE_SUFFIX"

    /** 16-bit id of a full UUID string when it is a SIG-base UUID, else null. */
    fun short(full: String): String? {
        val lower = full.lowercase()
        return if (lower.length == 36 && lower.startsWith("0000") && lower.endsWith(BASE_SUFFIX)) {
            lower.substring(4, 8)
        } else {
            null
        }
    }
}

/** What a scale exposes after service discovery, reduced to 16-bit ids. */
data class GattFingerprint(
    val services: Set<String>,
    /** characteristic id -> the service it lives in. */
    val characteristics: Map<String, String>,
) {
    fun has(characteristic: String) = characteristic in characteristics
}

object DialectDetector {
    /**
     * Picks the dialect for a discovered scale, or null when none matches. Absence is as
     * informative as presence: FFB3 tells the A3 family (Robi S9, Speediance) apart from
     * the Chipsea 20-byte family, which has only FFB1 and FFB2 under FFB0.
     */
    fun detect(fingerprint: GattFingerprint): Dialect? = when {
        GattIds.SERVICE_FFB0 in fingerprint.services &&
            fingerprint.has(GattIds.CHAR_FFB2_NOTIFY) &&
            !fingerprint.has(GattIds.CHAR_FFB3) -> Dialect.CHIPSEA_AC20
        else -> null
    }

    /** The notify characteristic a dialect's frames arrive on. */
    fun notifyCharacteristic(dialect: Dialect): String = when (dialect) {
        Dialect.CHIPSEA_AC20 -> GattIds.CHAR_FFB2_NOTIFY
    }
}
