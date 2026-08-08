package blackark.app.vr.di

import android.content.Context
import blackark.app.vr.data.database.AppDatabase
import blackark.app.vr.data.repository.AvLibraryRepository
import blackark.app.vr.data.repository.MetadataScopeRepository
import blackark.app.vr.data.repository.QuickAccessRepository
import blackark.app.vr.data.repository.ServerRepository
import blackark.app.vr.data.repository.VideoDisplaySettingsRepository
import blackark.app.vr.data.repository.VideoRepository
import blackark.app.vr.data.security.SmbCredentialStore
import blackark.app.vr.remote.AppUpdateManager
import blackark.app.vr.remote.GitHubCredentialStore
import blackark.app.vr.remote.RuntimeConfigRepository

/** Small manual dependency container; the app remains a single module. */
class AppContainer(context: Context) {
    val githubCredentialStore = GitHubCredentialStore(context)
    val runtimeConfigRepository = RuntimeConfigRepository(context, githubCredentialStore)
    val appUpdateManager = AppUpdateManager(context, githubCredentialStore)
    val database: AppDatabase = AppDatabase.getDatabase(context.applicationContext)
    val credentialStore = SmbCredentialStore(context)
    val serverRepository = ServerRepository(database.serverDao())
    val metadataScopeRepository = MetadataScopeRepository(database.metadataScopeDao())
    val quickAccessRepository = QuickAccessRepository(database.quickAccessFolderDao())
    val videoRepository = VideoRepository(database.videoDao(), database.favoriteVideoDao())
    val videoDisplaySettingsRepository =
        VideoDisplaySettingsRepository(database.videoDisplaySettingsDao())
    val avLibraryRepository = AvLibraryRepository(
        avLibraryDao = database.avLibraryDao(),
        virtualGroupMetadataDao = database.virtualGroupMetadataDao(),
    )
}
