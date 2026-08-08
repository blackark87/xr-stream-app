package blackark.app.vr

import android.view.InputDevice
import android.view.MotionEvent
import blackark.app.vr.remote.RuntimeConfigRegistry
import kotlin.math.abs
import kotlin.math.max

internal data class ControllerAxisPair(
    val label: String,
    val xAxis: Int,
    val yAxis: Int,
)

internal data class ControllerAxisRange(
    val axis: Int,
    val minimum: Float,
    val maximum: Float,
    val flat: Float,
)

internal data class ResolvedControllerAxisSample(
    val x: Float,
    val y: Float,
    val eventTimeMs: Long,
    val deviceId: Int,
    val profileLabel: String,
    val profileChanged: Boolean,
)

internal val SUPPORTED_CONTROLLER_AXIS_PAIRS = listOf(
    ControllerAxisPair("X/Y", MotionEvent.AXIS_X, MotionEvent.AXIS_Y),
    ControllerAxisPair("HAT_X/HAT_Y", MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y),
    ControllerAxisPair("Z/RZ", MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ),
    ControllerAxisPair("RX/RY", MotionEvent.AXIS_RX, MotionEvent.AXIS_RY),
)

internal fun selectControllerAxisPair(
    ranges: List<ControllerAxisRange>,
    currentValues: Map<Int, Float>,
    isControllerSource: Boolean,
): ControllerAxisPair? {
    val rangesByAxis = ranges.associateBy(ControllerAxisRange::axis)
    return SUPPORTED_CONTROLLER_AXIS_PAIRS.firstOrNull { pair ->
        val xRange = rangesByAxis[pair.xAxis]
        val yRange = rangesByAxis[pair.yAxis]
        val hasCenteredRanges =
            xRange?.isCenteredBidirectional() == true &&
                yRange?.isCenteredBidirectional() == true
        val hasActiveValues =
            abs(
                normalizeControllerAxis(
                    currentValues[pair.xAxis] ?: 0f,
                    xRange,
                )
            ) >= RuntimeConfigRegistry.current.controller.axisProfileThreshold ||
                abs(
                    normalizeControllerAxis(
                        currentValues[pair.yAxis] ?: 0f,
                        yRange,
                    )
                ) >= RuntimeConfigRegistry.current.controller.axisProfileThreshold
        (hasCenteredRanges && hasActiveValues) ||
            (isControllerSource && xRange == null && yRange == null && hasActiveValues)
    }
}

internal fun normalizeControllerAxis(
    rawValue: Float,
    range: ControllerAxisRange?,
): Float {
    val extent = range?.let { max(abs(it.minimum), abs(it.maximum)) }?.coerceAtLeast(1f) ?: 1f
    val normalized = (rawValue / extent).coerceIn(-1f, 1f)
    val deadzone = max(
        (range?.flat ?: 0f) / extent,
        RuntimeConfigRegistry.current.controller.deadZone,
    )
        .coerceIn(0f, 0.95f)
    val magnitude = abs(normalized)
    if (magnitude < deadzone) return 0f
    val rescaled = ((magnitude - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
    return if (normalized < 0f) -rescaled else rescaled
}

internal fun isControllerSource(source: Int): Boolean =
    (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

internal class ControllerAxisResolver {
    private val profiles = mutableMapOf<Int, ControllerAxisPair>()

    fun clearDevice(deviceId: Int) {
        profiles.remove(deviceId)
    }

    fun resolveSamples(event: MotionEvent): List<ResolvedControllerAxisSample> {
        val deviceRanges = event.device?.motionRanges.orEmpty()
        val sourceRanges = deviceRanges.filter { range ->
            range.source == event.source ||
                (event.source and range.source) == range.source
        }
        val relevantRanges = if (sourceRanges.isNotEmpty()) sourceRanges else deviceRanges
        val ranges = relevantRanges.map { range ->
            ControllerAxisRange(
                axis = range.axis,
                minimum = range.min,
                maximum = range.max,
                flat = range.flat,
            )
        }
        val existingProfile = profiles[event.deviceId]
        var activationSampleIndex = event.historySize
        val profile = existingProfile ?: run {
            var selected: ControllerAxisPair? = null
            for (sampleIndex in 0..event.historySize) {
                val historyIndex = sampleIndex.takeIf { it < event.historySize }
                val values = SUPPORTED_CONTROLLER_AXIS_PAIRS
                    .flatMap { pair -> listOf(pair.xAxis, pair.yAxis) }
                    .distinct()
                    .associateWith { axis -> event.axisValue(axis, historyIndex) }
                selected = selectControllerAxisPair(
                    ranges = ranges,
                    currentValues = values,
                    isControllerSource = isControllerSource(event.source),
                )
                if (selected != null) {
                    activationSampleIndex = sampleIndex
                    break
                }
            }
            selected
        } ?: return emptyList()
        val profileChanged = existingProfile == null
        if (profileChanged) {
            profiles[event.deviceId] = profile
        }

        val rangesByAxis = ranges.associateBy(ControllerAxisRange::axis)
        val samples = ArrayList<ResolvedControllerAxisSample>(event.historySize + 1)
        for (historyIndex in 0 until event.historySize) {
            samples += buildSample(
                event = event,
                profile = profile,
                rangesByAxis = rangesByAxis,
                historyIndex = historyIndex,
                profileChanged = profileChanged && historyIndex == activationSampleIndex,
            )
        }
        samples += buildSample(
            event = event,
            profile = profile,
            rangesByAxis = rangesByAxis,
            historyIndex = null,
            profileChanged = profileChanged && activationSampleIndex == event.historySize,
        )
        return samples
    }

    private fun buildSample(
        event: MotionEvent,
        profile: ControllerAxisPair,
        rangesByAxis: Map<Int, ControllerAxisRange>,
        historyIndex: Int?,
        profileChanged: Boolean,
    ): ResolvedControllerAxisSample {
        val rawX = event.axisValue(profile.xAxis, historyIndex)
        val rawY = event.axisValue(profile.yAxis, historyIndex)
        val eventTime =
            historyIndex?.let(event::getHistoricalEventTime) ?: event.eventTime
        return ResolvedControllerAxisSample(
            x = normalizeControllerAxis(rawX, rangesByAxis[profile.xAxis]),
            y = normalizeControllerAxis(rawY, rangesByAxis[profile.yAxis]),
            eventTimeMs = eventTime,
            deviceId = event.deviceId,
            profileLabel = profile.label,
            profileChanged = profileChanged,
        )
    }

    companion object {
        fun isControllerLikeInputDevice(device: InputDevice): Boolean {
            if (
                device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
                device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                device.supportsSource(InputDevice.SOURCE_DPAD)
            ) {
                return true
            }
            val supportedAxes = SUPPORTED_CONTROLLER_AXIS_PAIRS
                .flatMap { pair -> listOf(pair.xAxis, pair.yAxis) }
                .toSet()
            return device.motionRanges.any { range ->
                range.axis in supportedAxes &&
                    ControllerAxisRange(
                        axis = range.axis,
                        minimum = range.min,
                        maximum = range.max,
                        flat = range.flat,
                    ).isCenteredBidirectional()
            }
        }
    }
}

private fun ControllerAxisRange.isCenteredBidirectional(): Boolean =
    minimum < 0f && maximum > 0f && max(abs(minimum), abs(maximum)) <= 2f

private fun MotionEvent.axisValue(axis: Int, historyIndex: Int?): Float =
    if (historyIndex == null || pointerCount == 0) {
        getAxisValue(axis)
    } else {
        getHistoricalAxisValue(axis, 0, historyIndex)
    }
