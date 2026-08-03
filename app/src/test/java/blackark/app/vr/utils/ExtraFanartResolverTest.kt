package blackark.app.vr.utils

import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtraFanartResolverTest {

    @Test
    fun `지원 이미지 파일만 자연 정렬한다`() {
        val selected = selectExtraFanartFiles(
            listOf(
                file("fanart10.jpg"),
                file("fanart2.PNG"),
                file("fanart1.webp"),
                file("notes.txt"),
                folder("nested"),
            ),
        )

        assertEquals(
            listOf("fanart1.webp", "fanart2.PNG", "fanart10.jpg"),
            selected.map(SMBFileItem::name),
        )
    }

    @Test
    fun `지원 이미지가 없으면 빈 목록을 반환한다`() {
        assertEquals(
            emptyList<SMBFileItem>(),
            selectExtraFanartFiles(listOf(file("readme.txt"), folder("images"))),
        )
    }

    private fun file(name: String) = SMBFileItem(
        name = name,
        path = "smb://nas/work/extrafanart/$name",
        isDirectory = false,
        size = 1L,
        lastModified = 1L,
    )

    private fun folder(name: String) = SMBFileItem(
        name = name,
        path = "smb://nas/work/extrafanart/$name",
        isDirectory = true,
        size = 0L,
        lastModified = 1L,
    )
}
