package blackark.app.vr.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            """{"schemaVersion":1,"channel":"internal","runtime":{}}"""

        val selected = firstValidRemoteManifest(sequenceOf("not-json", previous))

        assertEquals(1, selected?.schemaVersion)
        assertEquals(RuntimeConfig(), selected?.runtime)
    }
}
