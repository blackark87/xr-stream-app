package blackark.app.vr.unity

/** One HTTP byte range. Null means no Range header; invalid ranges throw. */
data class HttpByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
    companion object {
        fun parse(header: String?, size: Long): HttpByteRange? {
            if (header == null) return null
            require(size > 0 && header.startsWith("bytes=") && ',' !in header)
            val parts = header.removePrefix("bytes=").split('-', limit = 2)
            require(parts.size == 2 && parts.any { it.isNotEmpty() })
            if (parts[0].isEmpty()) {
                val suffix = parts[1].toLong()
                require(suffix > 0)
                return HttpByteRange((size - suffix).coerceAtLeast(0), size - 1)
            }
            val start = parts[0].toLong()
            val end = if (parts[1].isEmpty()) size - 1 else parts[1].toLong().coerceAtMost(size - 1)
            require(start >= 0 && start < size && end >= start)
            return HttpByteRange(start, end)
        }
    }
}
