package blackark.app.vr.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {

    @Test
    fun `only a greater version code offers an update`() {
        assertFalse(shouldOfferAppUpdate(installedVersionCode = 10, releaseVersionCode = null))
        assertFalse(shouldOfferAppUpdate(installedVersionCode = 10, releaseVersionCode = 9))
        assertFalse(shouldOfferAppUpdate(installedVersionCode = 10, releaseVersionCode = 10))
        assertTrue(shouldOfferAppUpdate(installedVersionCode = 10, releaseVersionCode = 11))
    }

    @Test
    fun `matching package hash version and certificates pass verification`() {
        verifyDownloadedArtifact(
            expectedPackageName = "blackark.app.vr",
            expectedRelease = release(),
            actualPackageName = "blackark.app.vr",
            actualVersionCode = 11,
            actualVersionName = "0.1.0-internal.20260808T000000+abcdef0",
            actualSizeBytes = 100,
            actualSha256 = "a".repeat(64),
            installedSigningCertificateSha256 = "b".repeat(64),
            archiveSigningCertificateSha256 = "b".repeat(64),
        )
    }

    @Test
    fun `a package mismatch blocks installation`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyDownloadedArtifact(
                expectedPackageName = "blackark.app.vr",
                expectedRelease = release(),
                actualPackageName = "other.app",
                actualVersionCode = 11,
                actualVersionName = "0.1.0-internal.20260808T000000+abcdef0",
                actualSizeBytes = 100,
                actualSha256 = "a".repeat(64),
                installedSigningCertificateSha256 = "b".repeat(64),
                archiveSigningCertificateSha256 = "b".repeat(64),
            )
        }
    }

    @Test
    fun `a version mismatch blocks installation`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyDownloadedArtifact(
                expectedPackageName = "blackark.app.vr",
                expectedRelease = release(),
                actualPackageName = "blackark.app.vr",
                actualVersionCode = 12,
                actualVersionName = "0.1.0-internal.20260808T000001+abcdef0",
                actualSizeBytes = 100,
                actualSha256 = "a".repeat(64),
                installedSigningCertificateSha256 = "b".repeat(64),
                archiveSigningCertificateSha256 = "b".repeat(64),
            )
        }
    }

    @Test
    fun `a hash or signing mismatch blocks installation`() {
        assertThrows(IllegalArgumentException::class.java) {
            verifyDownloadedArtifact(
                expectedPackageName = "blackark.app.vr",
                expectedRelease = release(),
                actualPackageName = "blackark.app.vr",
                actualVersionCode = 11,
                actualVersionName = "0.1.0-internal.20260808T000000+abcdef0",
                actualSizeBytes = 100,
                actualSha256 = "c".repeat(64),
                installedSigningCertificateSha256 = "b".repeat(64),
                archiveSigningCertificateSha256 = "b".repeat(64),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            verifyDownloadedArtifact(
                expectedPackageName = "blackark.app.vr",
                expectedRelease = release(),
                actualPackageName = "blackark.app.vr",
                actualVersionCode = 11,
                actualVersionName = "0.1.0-internal.20260808T000000+abcdef0",
                actualSizeBytes = 100,
                actualSha256 = "a".repeat(64),
                installedSigningCertificateSha256 = "c".repeat(64),
                archiveSigningCertificateSha256 = "b".repeat(64),
            )
        }
    }

    private fun release() = RemoteRelease(
        versionCode = 11,
        versionName = "0.1.0-internal.20260808T000000+abcdef0",
        gitSha = "abcdef0",
        publishedAt = "2026-08-08T00:00:00Z",
        assetId = 5,
        sizeBytes = 100,
        sha256 = "a".repeat(64),
        signingCertificateSha256 = "b".repeat(64),
    )
}
