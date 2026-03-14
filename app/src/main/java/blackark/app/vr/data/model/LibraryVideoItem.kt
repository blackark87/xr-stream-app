package blackark.app.vr.data.model

interface LibraryVideoItem {
    val fileName: String
    val filePath: String
    val serverAddress: String
    val shareName: String
    val thumbnailPath: String?
    val resolvedTitle: String?
    val lastPosition: Long
        get() = 0L
    val duration: Long
        get() = 0L
}
