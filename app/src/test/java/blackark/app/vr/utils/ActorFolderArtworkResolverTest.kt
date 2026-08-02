package blackark.app.vr.utils

import blackark.app.vr.network.SMBFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActorFolderArtworkResolverTest {

    @Test
    fun `selects the actor image whose name matches the folder`() {
        val selected = selectActorFolderArtwork(
            folderName = "모리 히나코",
            files = listOf(image("다른 배우.jpg"), image("모리 히나코.jpg")),
        )

        assertEquals("모리 히나코.jpg", selected?.name)
    }

    @Test
    fun `prefers jpg then jpeg png and webp for exact matches`() {
        val selected = selectActorFolderArtwork(
            folderName = "모리 히나코",
            files = listOf(
                image("모리 히나코.webp"),
                image("모리 히나코.png"),
                image("모리 히나코.jpeg"),
                image("모리 히나코.jpg"),
            ),
        )

        assertEquals("모리 히나코.jpg", selected?.name)
    }

    @Test
    fun `uses the first supported image by name when no name matches`() {
        val selected = selectActorFolderArtwork(
            folderName = "모리 히나코",
            files = listOf(image("b.jpg"), image("A.png"), image("c.webp")),
        )

        assertEquals("A.png", selected?.name)
    }

    @Test
    fun `returns null for an empty actor image folder`() {
        val selected = selectActorFolderArtwork(
            folderName = "모리 히나코",
            files = listOf(
                image("notes.txt"),
                image("모리 히나코.jpg", isDirectory = true),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun `normalizes unicode whitespace and case before matching`() {
        val selected = selectActorFolderArtwork(
            folderName = "  MÖRI HINAKO  ",
            files = listOf(image("MO\u0308RI\u3000HINAKO.PNG"), image("other.jpg")),
        )

        assertEquals("MO\u0308RI\u3000HINAKO.PNG", selected?.name)
    }

    private fun image(
        name: String,
        isDirectory: Boolean = false,
    ) = SMBFileItem(
        name = name,
        path = "smb://server/share/.actors/$name",
        isDirectory = isDirectory,
        size = 1L,
        lastModified = 0L,
    )
}
