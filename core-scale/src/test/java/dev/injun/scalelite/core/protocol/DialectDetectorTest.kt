package dev.injun.scalelite.core.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DialectDetectorTest {

    @Test
    fun `T6_AF502 tree is the Chipsea 20-byte dialect`() {
        assertEquals(Dialect.CHIPSEA_AC20, DialectDetector.detect(Fixtures.t6Fingerprint))
    }

    @Test
    fun `an FFB0 tree that also exposes FFB3 is not claimed`() {
        val a3 = Fixtures.t6Fingerprint.copy(
            characteristics = Fixtures.t6Fingerprint.characteristics + ("ffb3" to "ffb0"),
        )
        assertNull(DialectDetector.detect(a3))
    }

    @Test
    fun `a device without FFB0 is not claimed`() {
        assertNull(DialectDetector.detect(GattFingerprint(setOf("1800", "1801", "180a"), emptyMap())))
    }

    @Test
    fun `short and full UUID forms round-trip`() {
        assertEquals("0000ffb2-0000-1000-8000-00805f9b34fb", GattIds.full("FFB2"))
        assertEquals("ffb2", GattIds.short("0000FFB2-0000-1000-8000-00805F9B34FB"))
        assertNull(GattIds.short("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
    }
}
