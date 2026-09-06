package blackark.app.vr.ui.components

/** Rejects queued clicks after a layer is disabled, replaced, or disposed. */
internal class DeferredInputGate {
    var generation: Long = 0
        private set
    private var active = false

    fun activate(enabled: Boolean) {
        generation++
        active = enabled
    }

    fun accepts(eventGeneration: Long): Boolean = active && generation == eventGeneration
}
