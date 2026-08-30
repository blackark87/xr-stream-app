package blackark.app.vr.player

import blackark.app.vr.network.SMBConfig

sealed interface PlaybackSource {
    data class Smb(
        val config: SMBConfig,
    ) : PlaybackSource

    data class Local(
        val rootTreeUri: String? = null,
    ) : PlaybackSource

    /** A prepared URI whose lifetime is owned by an external source such as the WSD bridge. */
    data class Direct(
        val mediaUri: String,
        val sourceName: String,
    ) : PlaybackSource
}
