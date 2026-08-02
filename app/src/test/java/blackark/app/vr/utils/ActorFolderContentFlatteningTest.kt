package blackark.app.vr.utils

import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActorFolderContentFlatteningTest {

    @Test
    fun `japan 바로 아래 배우 폴더를 식별한다`() {
        assertTrue(
            isJapanActorFolderPath(
                "smb://nas/Videos/AV/japan/모리 히나코",
            ),
        )
        assertTrue(
            isJapanActorFolderPath(
                "content://provider/document/volume%3AVideos%2FAV%2Fjapan%2F모리%20히나코",
            ),
        )
        assertFalse(isJapanActorFolderPath("smb://nas/Videos/AV/japan"))
        assertFalse(
            isJapanActorFolderPath(
                "smb://nas/Videos/AV/japan/모리 히나코/MIKR-109",
            ),
        )
    }

    @Test
    fun `영상이 있는 content 폴더를 실제 영상으로 평탄화한다`() {
        val directVideo = file("DIRECT-001.mp4")
        val contentFolder = folder("MIKR-109")
        val unavailableFolder = folder("UNKNOWN")
        val nestedVideo = file(
            name = "MIKR-109.mp4",
            path = "smb://nas/japan/actor/MIKR-109/MIKR-109.mp4",
        )

        val flattened = flattenActorContentDirectories(
            actorFolderChildren = listOf(directVideo, contentFolder, unavailableFolder),
            contentFolderChildrenByPath = mapOf(
                contentFolder.path to listOf(
                    file("MIKR-109.nfo"),
                    file("poster.jpg"),
                    nestedVideo,
                ),
            ),
        )

        assertEquals(
            listOf(directVideo.path, nestedVideo.path, unavailableFolder.path),
            flattened.map { file -> file.path },
        )
    }

    @Test
    fun `multipart 영상은 모두 유지해 후속 가상그룹 처리를 허용한다`() {
        val contentFolder = folder("CODE-123")
        val part1 = file("CODE-123-pt-1.mp4")
        val part2 = file("CODE-123-pt-2.mp4")

        val flattened = flattenActorContentDirectories(
            actorFolderChildren = listOf(contentFolder),
            contentFolderChildrenByPath = mapOf(
                contentFolder.path to listOf(part1, part2),
            ),
        )

        assertEquals(listOf(part1, part2), flattened)
    }

    @Test
    fun `실제 부모 content 폴더명을 metadata base name으로 해석한다`() {
        assertEquals(
            "MIKR-109",
            resolveParentFolderBaseName(
                "smb://nas/Videos/AV/japan/모리 히나코/MIKR-109/MIKR-109.mp4",
            ),
        )
        assertEquals(
            "MIKR-109",
            resolveParentFolderBaseName(
                "content://provider/document/volume%3AVideos%2FAV%2Fjapan%2F모리%20히나코%2FMIKR-109%2FMIKR-109.mp4",
            ),
        )
    }

    private fun folder(name: String): SMBFileItem {
        return SMBFileItem(
            name = name,
            path = "smb://nas/japan/actor/$name",
            isDirectory = true,
            size = 0L,
            lastModified = 1L,
        )
    }

    private fun file(
        name: String,
        path: String = "smb://nas/japan/actor/$name",
    ): SMBFileItem {
        return SMBFileItem(
            name = name,
            path = path,
            isDirectory = false,
            size = 100L,
            lastModified = 1L,
        )
    }
}
