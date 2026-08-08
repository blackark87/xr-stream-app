package blackark.app.vr

import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerAxisResolverTest {

    @Test
    fun `selects every supported centered controller axis pair`() {
        SUPPORTED_CONTROLLER_AXIS_PAIRS.forEach { pair ->
            val selected = selectControllerAxisPair(
                ranges = listOf(centeredRange(pair.xAxis), centeredRange(pair.yAxis)),
                currentValues = mapOf(pair.xAxis to 0.8f, pair.yAxis to 0f),
                isControllerSource = true,
            )

            assertEquals(pair.label, selected?.label)
        }
    }

    @Test
    fun `selects first active pair instead of an idle higher priority pair`() {
        val selected = selectControllerAxisPair(
            ranges = listOf(
                centeredRange(MotionEvent.AXIS_X),
                centeredRange(MotionEvent.AXIS_Y),
                centeredRange(MotionEvent.AXIS_Z),
                centeredRange(MotionEvent.AXIS_RZ),
            ),
            currentValues = mapOf(
                MotionEvent.AXIS_X to 0f,
                MotionEvent.AXIS_Y to 0f,
                MotionEvent.AXIS_Z to 0.8f,
                MotionEvent.AXIS_RZ to 0f,
            ),
            isControllerSource = true,
        )

        assertEquals("Z/RZ", selected?.label)
    }

    @Test
    fun `range deadzone noise does not lock the wrong profile`() {
        val selected = selectControllerAxisPair(
            ranges = listOf(
                centeredRange(MotionEvent.AXIS_X, flat = 0.2f),
                centeredRange(MotionEvent.AXIS_Y, flat = 0.2f),
                centeredRange(MotionEvent.AXIS_Z),
                centeredRange(MotionEvent.AXIS_RZ),
            ),
            currentValues = mapOf(
                MotionEvent.AXIS_X to 0.1f,
                MotionEvent.AXIS_Y to 0f,
                MotionEvent.AXIS_Z to 0.8f,
                MotionEvent.AXIS_RZ to 0f,
            ),
            isControllerSource = true,
        )

        assertEquals("Z/RZ", selected?.label)
    }

    @Test
    fun `absolute pointer coordinates are not selected as a thumbstick`() {
        val selected = selectControllerAxisPair(
            ranges = listOf(
                ControllerAxisRange(MotionEvent.AXIS_X, 0f, 1920f, 0f),
                ControllerAxisRange(MotionEvent.AXIS_Y, 0f, 1080f, 0f),
            ),
            currentValues = mapOf(
                MotionEvent.AXIS_X to 960f,
                MotionEvent.AXIS_Y to 540f,
            ),
            isControllerSource = false,
        )

        assertNull(selected)
    }

    @Test
    fun `deadzone is removed and remaining axis is rescaled`() {
        val range = ControllerAxisRange(
            axis = MotionEvent.AXIS_X,
            minimum = -1f,
            maximum = 1f,
            flat = 0.1f,
        )

        assertEquals(0f, normalizeControllerAxis(0.05f, range), 0f)
        assertEquals(0.5f, normalizeControllerAxis(0.55f, range), 0.0001f)
        assertEquals(-0.5f, normalizeControllerAxis(-0.55f, range), 0.0001f)
    }

    @Test
    fun `controller source helper accepts joystick gamepad and dpad`() {
        assertEquals(true, isControllerSource(InputDevice.SOURCE_JOYSTICK))
        assertEquals(true, isControllerSource(InputDevice.SOURCE_GAMEPAD))
        assertEquals(true, isControllerSource(InputDevice.SOURCE_DPAD))
    }

    private fun centeredRange(axis: Int, flat: Float = 0.08f) = ControllerAxisRange(
        axis = axis,
        minimum = -1f,
        maximum = 1f,
        flat = flat,
    )
}
