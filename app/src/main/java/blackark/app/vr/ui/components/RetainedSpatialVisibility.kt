package blackark.app.vr.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.subspace.SceneCoreEntity
import androidx.xr.compose.subspace.SubspaceComposable
import androidx.xr.scenecore.Entity

/** Keeps layout and Android surfaces alive without shrinking or moving hidden panels.
 * Disabled ancestors suppress both rendering and hit testing, including child Android surfaces.
 */
@Composable
@SubspaceComposable
internal fun RetainedSpatialVisibility(
    name: String,
    visible: Boolean,
    content: @Composable @SubspaceComposable () -> Unit,
) {
    val session = LocalSession.current ?: return
    val factory = remember(session, name) {
        { Entity.create(session, name).apply { setEnabled(false) } }
    }
    SceneCoreEntity(
        factory = factory,
        update = { entity ->
            if (entity.isEnabled(includeParents = false) != visible) {
                entity.setEnabled(visible)
                Log.d("PlaybackLayerDebug", "layer=$name enabled=$visible")
            }
        },
        content = content,
    )
}
