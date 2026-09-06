package blackark.app.vr.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackRecenterTest {
    @Test
    fun `request uses state after three seconds instead of the button click`() = runBlocking {
        var elapsed = 0
        var headDirection = "button"
        val ticks = mutableListOf<Int>()
        val captured = mutableListOf<String>()
        runRecenterCountdown(
            onTick = { ticks += it },
            waitOneSecond = {
                elapsed++
                headDirection = "desired direction at $elapsed"
                assertEquals(0, captured.size)
            },
            onReady = { captured += headDirection },
        )
        assertEquals(listOf(3, 2, 1), ticks)
        assertEquals(listOf("desired direction at 3"), captured)
    }

    @Test
    fun `leaving during countdown cannot issue recenter`() = runBlocking {
        var requested = false
        try {
            runRecenterCountdown(
                onTick = {},
                waitOneSecond = { throw CancellationException("playback exited") },
                onReady = { requested = true },
            )
        } catch (_: CancellationException) {
            // The caller's lifecycle cancels the countdown.
        }
        assertFalse(requested)
    }
}
