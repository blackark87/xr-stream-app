package blackark.app.vr.ui

import androidx.xr.scenecore.InputEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTrackingSupportTest {

    @Test
    fun controllerInputIsAllowedWhenHandTrackingIsOff() {
        assertTrue(
            isSpatialInputSourceAllowed(
                isHandTrackingEnabled = false,
                source = InputEvent.Source.CONTROLLER,
            )
        )
    }

    @Test
    fun mouseInputIsAllowedWhenHandTrackingIsOff() {
        assertTrue(
            isSpatialInputSourceAllowed(
                isHandTrackingEnabled = false,
                source = InputEvent.Source.MOUSE,
            )
        )
    }

    @Test
    fun handInputIsRejectedWhenHandTrackingIsOff() {
        assertFalse(
            isSpatialInputSourceAllowed(
                isHandTrackingEnabled = false,
                source = InputEvent.Source.HANDS,
            )
        )
    }

    @Test
    fun gazeAndHeadInputAreRejectedWhenHandTrackingIsOff() {
        assertFalse(
            isSpatialInputSourceAllowed(
                isHandTrackingEnabled = false,
                source = InputEvent.Source.GAZE_AND_GESTURE,
            )
        )
        assertFalse(
            isSpatialInputSourceAllowed(
                isHandTrackingEnabled = false,
                source = InputEvent.Source.HEAD,
            )
        )
    }

    @Test
    fun allCommonSourcesAreAllowedWhenHandTrackingIsOn() {
        val sources = listOf(
            InputEvent.Source.CONTROLLER,
            InputEvent.Source.MOUSE,
            InputEvent.Source.HANDS,
            InputEvent.Source.GAZE_AND_GESTURE,
            InputEvent.Source.HEAD,
        )

        sources.forEach { source ->
            assertTrue(
                isSpatialInputSourceAllowed(
                    isHandTrackingEnabled = true,
                    source = source,
                )
            )
        }
    }
}
