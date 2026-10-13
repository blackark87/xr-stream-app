package blackark.app.vr.unity

import org.junit.Assert.*
import org.junit.Test

class HttpByteRangeTest {
    @Test fun supportsOpenClosedAndSuffixRanges() {
        assertEquals(HttpByteRange(20, 99), HttpByteRange.parse("bytes=20-", 100))
        assertEquals(HttpByteRange(20, 99), HttpByteRange.parse("bytes=20-200", 100))
        assertEquals(HttpByteRange(90, 99), HttpByteRange.parse("bytes=-10", 100))
        assertEquals(HttpByteRange(0, 99), HttpByteRange.parse("bytes=-200", 100))
        assertNull(HttpByteRange.parse(null, 100))
    }
    @Test fun rejectsUnsatisfiableAndMultipleRanges() {
        for (value in listOf("bytes=100-", "bytes=30-20", "bytes=-0", "bytes=0-1,5-6", "bytes=-", "items=0-1")) {
            assertThrows(IllegalArgumentException::class.java) { HttpByteRange.parse(value, 100) }
        }
    }
}
