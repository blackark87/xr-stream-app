package blackark.app.vr.ui.viewmodel

import kotlinx.coroutines.delay

/** Requests tracking only after the user has had time to look away from the UI button. */
internal suspend fun runRecenterCountdown(
    onTick: (Int) -> Unit,
    waitOneSecond: suspend () -> Unit = { delay(1_000) },
    onReady: () -> Unit,
) {
    for (remaining in 3 downTo 1) {
        onTick(remaining)
        waitOneSecond()
    }
    onReady()
}
