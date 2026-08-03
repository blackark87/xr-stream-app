package blackark.app.vr.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SmbCredentials(
    val username: String = "",
    val password: String = "",
    val domain: String = "",
) {
    val isGuest: Boolean get() = username.isBlank() && password.isBlank()
}

/**
 * Per-server credential storage backed by a non-auth-bound Android Keystore AES-GCM key.
 *
 * The encrypted payload lives in private preferences so an existing server can be pre-filled.
 * Neither Room nor preferences ever receive plaintext credentials.
 */
class SmbCredentialStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(alias: String): SmbCredentials {
        if (alias.isBlank()) return SmbCredentials()
        val encoded = preferences.getString(alias, null) ?: return SmbCredentials()
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
            decodePayload(cipher.doFinal(ciphertext).decodeToString())
        }.getOrElse {
            // A restored preference cannot be decrypted with a device-local Keystore key.
            preferences.edit().remove(alias).apply()
            SmbCredentials()
        }
    }

    @Synchronized
    fun save(alias: String, credentials: SmbCredentials) {
        require(alias.isNotBlank()) { "Credential alias must not be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(encodePayload(credentials).encodeToByteArray())
        val packed = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(alias, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun delete(alias: String) {
        if (alias.isNotBlank()) preferences.edit().remove(alias).apply()
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

    private fun encodePayload(credentials: SmbCredentials): String =
        listOf(credentials.username, credentials.password, credentials.domain)
            .joinToString(PAYLOAD_SEPARATOR) { value ->
                Base64.encodeToString(value.encodeToByteArray(), Base64.NO_WRAP)
            }

    private fun decodePayload(payload: String): SmbCredentials {
        val values = payload.split(PAYLOAD_SEPARATOR).map { value ->
            Base64.decode(value, Base64.NO_WRAP).decodeToString()
        }
        require(values.size == 3)
        return SmbCredentials(username = values[0], password = values[1], domain = values[2])
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "xr_stream_smb_credentials_v1"
        private const val PREFERENCES_NAME = "encrypted_smb_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val PAYLOAD_SEPARATOR = "."

        fun aliasForNewServer(): String = "smb-${java.util.UUID.randomUUID()}"
    }
}
