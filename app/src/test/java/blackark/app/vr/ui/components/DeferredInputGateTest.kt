package blackark.app.vr.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredInputGateTest {
    @Test fun `queued click cannot cross a hide and show transition`() {
        val gate = DeferredInputGate()
        gate.activate(true)
        val originalClick = gate.generation
        assertTrue(gate.accepts(originalClick))
        gate.activate(false)
        assertFalse(gate.accepts(originalClick))
        gate.activate(true)
        assertFalse(gate.accepts(originalClick))
        assertTrue(gate.accepts(gate.generation))
    }
}
