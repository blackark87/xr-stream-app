package blackark.app.vr.dmm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DmmSession(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String,
    val userId: String,
    val expiresAtEpochSeconds: Long,
    val secureId: String? = null,
    val uniqueId: String? = null,
) {
    fun hasUsableAccessToken(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        accessToken.isNotBlank() && expiresAtEpochSeconds > nowEpochSeconds + 30L
}

/** Stores DMM tokens and SessionID values encrypted with a device-local Android Keystore key. */
class DmmSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): DmmSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val ivSize = buffer.int
            require(ivSize in 12..32 && buffer.remaining() > ivSize)
            val iv = ByteArray(ivSize).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            decode(cipher.doFinal(ciphertext).decodeToString())
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun save(session: DmmSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(encode(session).encodeToByteArray())
        val packed = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(SESSION_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }

    private fun encode(session: DmmSession): String = JSONObject()
        .put("access_token", session.accessToken)
        .put("refresh_token", session.refreshToken)
        .put("id_token", session.idToken)
        .put("user_id", session.userId)
        .put("expires_at", session.expiresAtEpochSeconds)
        .put("secure_id", session.secureId)
        .put("unique_id", session.uniqueId)
        .toString()

    private fun decode(value: String): DmmSession = JSONObject(value).let { json ->
        DmmSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            idToken = json.getString("id_token"),
            userId = json.getString("user_id"),
            expiresAtEpochSeconds = json.getLong("expires_at"),
            secureId = json.optString("secure_id").takeIf { it.isNotBlank() },
            uniqueId = json.optString("unique_id").takeIf { it.isNotBlank() },
        )
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "xr_stream_dmm_session_v1"
        private const val PREFERENCES_NAME = "encrypted_dmm_session"
        private const val SESSION_KEY = "current"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }
}
