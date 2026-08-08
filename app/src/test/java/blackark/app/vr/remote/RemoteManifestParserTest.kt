package blackark.app.vr.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteManifestParserTest {

    @Test
    fun `GitHub response mapping recognizes etag and authorization statuses`() {
        assertEquals(
            GitHubManifestResponseDisposition.NotModified,
            classifyGitHubManifestResponse(304),
        )
        assertEquals(
            GitHubManifestResponseDisposition.Unauthorized,
            classifyGitHubManifestResponse(401),
        )
        assertEquals(
            GitHubManifestResponseDisposition.Forbidden,
            classifyGitHubManifestResponse(403),
        )
        assertEquals(
            GitHubManifestResponseDisposition.NotFound,
            classifyGitHubManifestResponse(404),
        )
        assertEquals(
            GitHubManifestResponseDisposition.Failure,
            classifyGitHubManifestResponse(503),
        )
    }

    @Test
    fun `unknown fields are ignored and runtime values are clamped`() {
        val manifest = RemoteManifestParser.parse(
            """
            {
              "schemaVersion": 1,
              "channel": "internal",
              "minimumAppVersionCode": 0,
              "unknownRoot": true,
              "runtime": {
                "preview": {
                  "focusDelayMs": 999999,
                  "defaultVolume": -5,
                  "unknownPreview": "ignored"
                },
                "controller": {
                  "engageThreshold": 0.2,
                  "releaseThreshold": 0.8
                },
                "subtitles": {
                  "minDistanceMeters": 3.0,
                  "maxDistanceMeters": 0.6,
                  "defaultDistanceMeters": 4.0
                },
                "metadata": {
                  "baseUrl": "http://example.com",
                  "contentIdPattern": "[",
                  "videoExtensions": [".MP4", "mkv", "../../bad", "mp4"],
                  "imageExtensions": ["jpg", "svg"],
                  "artworkPriority": ["nfoPoster", "unsupported", "generatedFrame"]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(1L, manifest.minimumAppVersionCode)
        assertNull(manifest.release)
        assertEquals(5_000L, manifest.runtime.preview.focusDelayMs)
        assertEquals(0f, manifest.runtime.preview.defaultVolume, 0f)
        assertTrue(
            manifest.runtime.controller.releaseThreshold <
                manifest.runtime.controller.engageThreshold
        )
        assertEquals(0.6f, manifest.runtime.subtitles.minDistanceMeters, 0f)
        assertEquals(3f, manifest.runtime.subtitles.maxDistanceMeters, 0f)
        assertEquals("https://jvrlibrary.com", manifest.runtime.metadata.baseUrl)
        assertEquals(RuntimeConfig().metadata.contentIdPattern, manifest.runtime.metadata.contentIdPattern)
        assertEquals(listOf("mp4", "mkv"), manifest.runtime.metadata.videoExtensions)
        assertEquals(listOf("jpg"), manifest.runtime.metadata.imageExtensions)
        assertEquals(
            listOf("nfoPoster", "generatedFrame"),
            manifest.runtime.metadata.artworkPriority,
        )
    }

    @Test
    fun `release is parsed only with valid hashes`() {
        val hash = "a".repeat(64)
        val certificate = "b".repeat(64)
        val manifest = RemoteManifestParser.parse(
            """
            {
              "schemaVersion": 1,
              "channel": "internal",
              "minimumAppVersionCode": 10,
              "runtime": {},
              "release": {
                "versionCode": 20,
                "versionName": "0.1.0-internal.20260808T000000+abcdef0",
                "gitSha": "abcdef0",
                "publishedAt": "2026-08-08T00:00:00Z",
                "assetId": 30,
                "sizeBytes": 40,
                "sha256": "$hash",
                "signingCertificateSha256": "$certificate",
                "mandatory": true
              }
            }
            """.trimIndent()
        )

        assertEquals(20L, manifest.release?.versionCode)
        assertEquals(30L, manifest.release?.assetId)
        assertTrue(manifest.release?.mandatory == true)
    }

    @Test
    fun `unsupported schema is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RemoteManifestParser.parse(
                """{"schemaVersion":2,"channel":"internal","runtime":{}}"""
            )
        }

        assertFalse(error.message.isNullOrBlank())
    }

    @Test
    fun `last known good manifest is selected when current cache is invalid`() {
        val previous =
            """{"schemaVersion":1,"channel":"internal","minimumAppVersionCode":1,"runtime":{}}"""

        val selected = firstValidRemoteManifest(sequenceOf("not-json", previous))

        assertEquals(1, selected?.schemaVersion)
        assertEquals(RuntimeConfig(), selected?.runtime)
    }
}
