package blackark.app.vr.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvLibraryModelsTest {

    @Test
    fun `explicit VR genres identify VR works`() {
        listOf("VR専用", "8KVR", "ハイクオリティVR", "バーチャルリアリティ").forEach { genre ->
            assertTrue(
                genre,
                isVrLibraryContent(
                    normalizedCode = "MIKR-109",
                    genres = listOf(genre),
                    representativePath = "/Videos/AV/japan/actor/MIKR-109.mp4",
                    representativeFileName = "MIKR-109.mp4",
                ),
            )
        }
    }

    @Test
    fun `VR marker in content id identifies locally indexed VR works`() {
        listOf("SAVR-441", "DSVR-1548", "MDVR-426", "VRKM-834").forEach { code ->
            assertTrue(
                code,
                isVrLibraryContent(
                    normalizedCode = code,
                    genres = emptyList(),
                    representativePath = "/Videos/AV/japan/actor/$code.mp4",
                    representativeFileName = "$code.mp4",
                ),
            )
        }
    }

    @Test
    fun `VR path and stereo filename remain valid indicators`() {
        assertTrue(
            isVrLibraryContent(
                normalizedCode = "CODE-123",
                genres = emptyList(),
                representativePath = "/Videos/AV/VR/CODE-123.mp4",
                representativeFileName = "CODE-123.mp4",
            ),
        )
        assertTrue(
            isVrLibraryContent(
                normalizedCode = "CODE-123",
                genres = emptyList(),
                representativePath = "/Videos/AV/japan/actor/CODE-123.SBS.mp4",
                representativeFileName = "CODE-123.SBS.mp4",
            ),
        )
    }

    @Test
    fun `ordinary work is not identified as VR`() {
        assertFalse(
            isVrLibraryContent(
                normalizedCode = "MIKR-109",
                genres = listOf("Drama", "Adventure"),
                representativePath = "/Videos/AV/japan/actor/MIKR-109.mp4",
                representativeFileName = "MIKR-109.mp4",
            ),
        )
    }
}
