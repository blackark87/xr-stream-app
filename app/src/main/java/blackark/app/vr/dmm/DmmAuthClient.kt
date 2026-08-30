package blackark.app.vr.dmm

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DmmAuthClient(
    private val config: DmmConfig,
) {
    suspend fun ensureActive(session: DmmSession): DmmSession = withContext(Dispatchers.IO) {
        requireConfigured()
        if (session.hasUsableAccessToken()) session else refresh(session)
    }

    suspend fun exchangeAuthorizationCode(code: String): DmmSession = withContext(Dispatchers.IO) {
        requireConfigured()
        val body = post(
            path = "/connect/v1/token",
            authorization = basicAuthorization(),
            payload = JSONObject()
                .put("grant_type", "authorization_code")
                .put("code", code)
                .put("redirect_uri", config.redirectUri),
        ).responseBody()
        body.toSession(previousRefreshToken = null)
    }

    suspend fun refresh(session: DmmSession): DmmSession = withContext(Dispatchers.IO) {
        requireConfigured()
        val refreshToken = requireNotNull(session.refreshToken) {
            "The DMM session expired and no refresh token is available. Sign in again."
        }
        val body = post(
            path = "/connect/v1/token",
            authorization = basicAuthorization(),
            payload = JSONObject()
                .put("grant_type", "refresh_token")
                .put("refresh_token", refreshToken),
        ).responseBody()
        body.toSession(previousRefreshToken = refreshToken)
    }

    suspend fun ensureSessionId(session: DmmSession): DmmSession = withContext(Dispatchers.IO) {
        val active = ensureActive(session)
        val body = post(
            path = "/connect/v1/issueSessionId",
            authorization = "Bearer ${active.accessToken}",
            payload = JSONObject().put("user_id", active.userId),
        ).responseBody()
        active.copy(
            uniqueId = body.getString("unique_id"),
            secureId = body.getString("secure_id"),
        )
    }

    private fun JSONObject.toSession(previousRefreshToken: String?): DmmSession {
        val idToken = getString("id_token")
        val payload = verifyAndDecodeIdToken(idToken)
        val expiresIn = optLong("expires_in", 3600L).coerceAtLeast(60L)
        return DmmSession(
            accessToken = getString("access_token"),
            refreshToken = optString("refresh_token").takeIf { it.isNotBlank() }
                ?: previousRefreshToken,
            idToken = idToken,
            userId = payload.getString("user_id"),
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + expiresIn,
        )
    }

    private fun verifyAndDecodeIdToken(jwt: String): JSONObject {
        val parts = jwt.split('.')
        require(parts.size == 3) { "DMM returned an invalid ID token." }
        val header = JSONObject(decodeBase64Url(parts[0]).decodeToString())
        require(header.optString("alg") == "HS512") { "Unsupported DMM ID-token algorithm." }

        val mac = Mac.getInstance("HmacSHA512").apply {
            init(SecretKeySpec(config.jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA512"))
        }
        val signed = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.UTF_8)
        val expected = mac.doFinal(signed)
        val actual = decodeBase64Url(parts[2])
        require(MessageDigest.isEqual(expected, actual)) { "DMM ID-token signature verification failed." }
        return JSONObject(decodeBase64Url(parts[1]).decodeToString())
    }

    private fun decodeBase64Url(value: String): ByteArray = Base64.decode(
        value,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun basicAuthorization(): String {
        val raw = "${config.clientId}:${config.clientSecret}".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.encodeToString(raw, Base64.NO_WRAP)}"
    }

    private fun post(path: String, authorization: String, payload: JSONObject): JSONObject {
        val connection = URL(config.apiBaseUrl.trimEnd('/') + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val bytes = payload.toString().toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw DmmApiException(status, responseText.take(512))
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.responseBody(): JSONObject {
        val header = optJSONObject("header")
        if (header != null && header.optInt("result_code", 0) != 0) {
            throw DmmApiException(header.optInt("result_code"), "DMM API rejected the request.")
        }
        return optJSONObject("body") ?: this
    }

    private fun requireConfigured() {
        check(config.isConfigured) {
            "DMM OAuth is not configured. Add the authorized DMM_* values to secrets.local.properties."
        }
    }
}

class DmmApiException(
    val statusCode: Int,
    detail: String,
) : Exception("DMM request failed ($statusCode): $detail")
