package blackark.app.vr.utils

import blackark.app.vr.data.database.entity.MetadataScope
import blackark.app.vr.data.repository.MetadataScopeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object MetadataScopePaths {
    fun canonicalize(path: String): String = path
        .substringBefore('?')
        .substringBefore('#')
        .replace('\\', '/')
        .replace(Regex("(?<!:)/+"), "/")
        .trim()
        .trimEnd('/')

    fun forServerFolder(
        serverAddress: String,
        port: Int,
        shareName: String,
        folderPath: String,
    ): String {
        if (folderPath.startsWith("smb://", ignoreCase = true)) return canonicalize(folderPath)
        val sharePart = shareName.trim('/').takeIf(String::isNotBlank)?.let { "/$it" }.orEmpty()
        val folderPart = folderPath.trim('/').takeIf(String::isNotBlank)?.let { "/$it" }.orEmpty()
        return canonicalize("smb://${serverAddress.trim()}:$port$sharePart$folderPart")
    }

    fun contains(scope: MetadataScope, candidatePath: String): Boolean {
        if (!scope.enabled) return false
        val root = canonicalize(scope.canonicalPath)
        val candidate = canonicalize(candidatePath)
        if (root.equals(candidate, ignoreCase = true)) return true
        if (!scope.includeDescendants || root.isBlank()) return false
        val boundary = "$root/"
        return candidate.regionMatches(0, boundary, 0, boundary.length, ignoreCase = true)
    }
}

/** Read-through snapshot used by the metadata pipeline without hardcoded folder names. */
object MetadataScopeRegistry {
    @Volatile
    private var scopes: List<MetadataScope> = emptyList()

    fun initialize(repository: MetadataScopeRepository, scope: CoroutineScope) {
        scope.launch {
            repository.allScopes.collectLatest { updated ->
                scopes = updated.filter(MetadataScope::enabled)
            }
        }
    }

    fun resolve(candidatePath: String): MetadataScope? = scopes
        .asSequence()
        .filter { MetadataScopePaths.contains(it, candidatePath) }
        .maxByOrNull { MetadataScopePaths.canonicalize(it.canonicalPath).length }

    fun isEnabled(candidatePath: String): Boolean = resolve(candidatePath) != null
}
