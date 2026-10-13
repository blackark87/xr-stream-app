package blackark.app.vr.unity

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LocalFolderPickerActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state == null) startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION), 7001)
    }
    @Deprecated("Standalone Unity bridge activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7001) return
        var uri: String? = null
        var failure: String? = null
        try {
            if (resultCode == RESULT_OK && data?.data != null) {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(data.data!!, flags)
                uri = data.data.toString()
            }
        } catch (error: Exception) {
            failure = error.message ?: "Unable to keep access to the selected folder"
        } finally {
            UnityBridge.completeLocalFolder(intent.getStringExtra("id").orEmpty(), intent.getLongExtra("generation", 0), uri, failure)
            finish()
        }
    }
}
