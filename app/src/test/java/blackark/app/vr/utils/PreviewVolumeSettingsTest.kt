package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewVolumeSettingsTest {

    @Test
    fun `preview volume is clamped to player range`() {
        assertEquals(0f, normalizePreviewVolume(-0.5f), 0f)
        assertEquals(0.4f, normalizePreviewVolume(0.4f), 0f)
        assertEquals(1f, normalizePreviewVolume(1.5f), 0f)
    }

    @Test
    fun `mute remembers audible volume and unmute restores it`() {
        val muted = togglePreviewMute(currentVolume = 0.65f, lastAudibleVolume = 1f)
        assertEquals(0f, muted.volume, 0f)
        assertEquals(0.65f, muted.lastAudibleVolume, 0f)

        val restored = togglePreviewMute(
            currentVolume = muted.volume,
            lastAudibleVolume = muted.lastAudibleVolume,
        )
        assertEquals(0.65f, restored.volume, 0f)
        assertEquals(0.65f, restored.lastAudibleVolume, 0f)
    }
}
