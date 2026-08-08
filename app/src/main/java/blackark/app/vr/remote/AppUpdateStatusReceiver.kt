package blackark.app.vr.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import blackark.app.vr.XRStreamApplication

class AppUpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? XRStreamApplication ?: return
        application.container.appUpdateManager.handleInstallStatus(intent)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "blackark.app.vr.action.APP_UPDATE_INSTALL_STATUS"
        const val EXTRA_VERSION_CODE = "version_code"
    }
}
