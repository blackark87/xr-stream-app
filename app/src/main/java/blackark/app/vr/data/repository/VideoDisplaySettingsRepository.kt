package blackark.app.vr.data.repository

import blackark.app.vr.data.database.dao.VideoDisplaySettingsDao
import blackark.app.vr.data.database.entity.VideoDisplaySettings

class VideoDisplaySettingsRepository(
    private val videoDisplaySettingsDao: VideoDisplaySettingsDao,
) {
    suspend fun getByPath(path: String): VideoDisplaySettings? {
        return videoDisplaySettingsDao.getByPath(path)
    }

    suspend fun saveDisplaySettings(
        filePath: String,
        videoFormat: String,
        stereoMode: String,
    ) {
        videoDisplaySettingsDao.upsert(
            VideoDisplaySettings(
                filePath = filePath,
                videoFormat = videoFormat,
                stereoMode = stereoMode,
            )
        )
    }
}
