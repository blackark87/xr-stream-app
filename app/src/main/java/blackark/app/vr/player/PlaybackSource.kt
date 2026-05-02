package blackark.app.vr.player

import blackark.app.vr.network.SMBConfig

sealed interface PlaybackSource {
    data class Smb(
        val config: SMBConfig,
    ) : PlaybackSource

    data class Local(
        val rootTreeUri: String? = null,
    ) : PlaybackSource
}
