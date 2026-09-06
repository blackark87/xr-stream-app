package blackark.app.vr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.xr.compose.spatial.Subspace
import androidx.xr.compose.subspace.SpatialMainPanel
import androidx.xr.compose.subspace.layout.MovePolicy
import androidx.xr.compose.subspace.layout.ResizePolicy
import androidx.xr.compose.subspace.layout.SubspaceModifier
import androidx.xr.compose.subspace.layout.height
import androidx.xr.compose.subspace.layout.width
import androidx.xr.compose.subspace.layout.offset
import androidx.xr.compose.subspace.layout.rotate
import androidx.xr.compose.subspace.layout.movable
import androidx.xr.compose.subspace.layout.resizable
import androidx.xr.compose.subspace.layout.onGloballyPositioned
import androidx.xr.compose.unit.DpVolumeSize
import blackark.app.vr.AppState
import blackark.app.vr.ui.components.RetainedSpatialVisibility

/** The activity main panel has one owner across browser and player destinations. */
@Composable
internal fun DashboardPanelHost(visible: Boolean) {
    val density = LocalDensity.current
    val size by AppState.dashboardPanelSize.collectAsState()
    val savedPose by AppState.dashboardPanelPose.collectAsState()
    Subspace {
        RetainedSpatialVisibility("dashboard", visible) {
            var placement = SubspaceModifier.width(size.widthDp.dp).height(size.heightDp.dp)
            savedPose?.let { pose ->
                placement = placement.offset(
                    x = with(density) { pose.translation.x.toDp() },
                    y = with(density) { pose.translation.y.toDp() },
                    z = with(density) { pose.translation.z.toDp() },
                ).rotate(pose.rotation)
            }
            SpatialMainPanel(
                modifier = placement
                    .onGloballyPositioned { coordinates ->
                        if (AppState.dashboardPanelPose.value == null) {
                            AppState.updateDashboardPanelPose(coordinates.poseInRoot)
                        }
                    }
                    .movable(movePolicy = MovePolicy.system { event ->
                        AppState.updateDashboardPanelPose(event.pose)
                    })
                    .resizable(
                        minimumSize = DpVolumeSize(760.dp, 480.dp, 0.dp),
                        resizePolicy = ResizePolicy.custom { event ->
                            if (visible && event.size.width > 0 && event.size.height > 0) {
                                AppState.updateDashboardPanelSize(
                                    with(density) { event.size.width.toDp().value },
                                    with(density) { event.size.height.toDp().value },
                                )
                            }
                        },
                    ),
            )
        }
    }
}
