package blackark.app.vr.remote

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

class GitHubCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun getToken(): String? {
        val encoded = preferences.getString(KEY_TOKEN, null) ?: return null
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
            cipher.doFinal(ciphertext).decodeToString().trim().takeIf(String::isNotBlank)
        }.getOrElse {
            preferences.edit().remove(KEY_TOKEN).apply()
            null
        }
    }

    @Synchronized
    fun saveToken(token: String) {
        val normalized = token.trim()
        require(normalized.isNotBlank()) { "GitHub token must not be blank" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(normalized.encodeToByteArray())
        val packed = ByteBuffer.allocate(Int.SIZE_BYTES + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun clearToken() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    fun hasToken(): Boolean = getToken() != null

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

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "xr_stream_github_credentials_v1"
        private const val PREFERENCES_NAME = "encrypted_github_credentials"
        private const val KEY_TOKEN = "fine_grained_pat"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }
}
