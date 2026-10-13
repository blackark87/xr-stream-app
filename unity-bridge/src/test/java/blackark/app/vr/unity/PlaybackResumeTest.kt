package blackark.app.vr.unity

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumeTest {
    @Test fun resumesUnfinishedVideo() {
        assertEquals(48000L, playbackResumePositionMs(48000, 100000))
        assertEquals(94999L, playbackResumePositionMs(94999, 100000))
    }
    @Test fun replaysCompletedVideoFromBeginning() {
        assertEquals(0L, playbackResumePositionMs(95000, 100000))
        assertEquals(0L, playbackResumePositionMs(100000, 100000))
        assertEquals(0L, playbackResumePositionMs(120000, 100000))
    }
    @Test fun doesNotSeekUnknownOrInvalidHistory() {
        assertEquals(0L, playbackResumePositionMs(20000, 0))
        assertEquals(0L, playbackResumePositionMs(-1, 100000))
        assertEquals(0L, playbackResumePositionMs(20000, -1))
    }
}
