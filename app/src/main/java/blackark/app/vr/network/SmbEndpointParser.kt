package blackark.app.vr.network

data class ParsedSmbEndpoint(
    val address: String,
    val port: Int = DEFAULT_SMB_PORT,
    val shareName: String = "",
    val suggestedName: String = address,
) {
    companion object {
        const val DEFAULT_SMB_PORT = 445
    }
}

/** Accepts a hostname/IP or smb://host[:port][/share] without making advanced fields mandatory. */
object SmbEndpointParser {
    fun parse(rawValue: String): Result<ParsedSmbEndpoint> = runCatching {
        val input = rawValue.trim()
        require(input.isNotEmpty()) { "Enter a server address" }
        require(!input.contains('@')) { "Enter credentials in the user ID and password fields" }

        val uriText = if (input.startsWith("smb://", ignoreCase = true)) input else "smb://$input"
        val uri = java.net.URI(uriText.replace("\\", "/"))
        val host = uri.host?.trim().orEmpty()
        require(host.isNotEmpty()) { "Invalid SMB server address" }
        val port = if (uri.port == -1) ParsedSmbEndpoint.DEFAULT_SMB_PORT else uri.port
        require(port in 1..65535) { "SMB port must be between 1 and 65535" }
        val share = uri.path.orEmpty()
            .trim('/')
            .substringBefore('/')
            .trim()

        ParsedSmbEndpoint(
            address = host,
            port = port,
            shareName = share,
            suggestedName = host,
        )
    }
}
