package com.kunzisoft.keepass.utils

/**
 * Builds short, human readable hints to tell apart databases that share the same name.
 * Pure string handling so it can be unit tested without Android.
 */
object DatabaseLocationHint {

    private const val DOCUMENT_SEGMENT = "document/"

    /**
     * Returns the folder that contains the database, from its decoded URI or path,
     * keeping at most [maxSegments] trailing folders (for example "Documents/Work").
     * Returns null when no readable folder can be found (cloud providers often use opaque ids).
     */
    fun parentFolder(decodedPath: String?, maxSegments: Int = 2): String? {
        if (decodedPath.isNullOrBlank()) return null
        var path: String = decodedPath.trim()
        val schemeIndex = path.indexOf("://")
        if (schemeIndex >= 0) {
            val afterScheme = path.substring(schemeIndex + 3)
            path = if (path.startsWith("file", ignoreCase = true)) {
                afterScheme
            } else {
                // Drop the authority of a content URI
                afterScheme.substringAfter('/', "")
            }
        }
        // Storage Access Framework URI: keep only the document id
        val documentIndex = path.lastIndexOf(DOCUMENT_SEGMENT)
        if (documentIndex >= 0) {
            path = path.substring(documentIndex + DOCUMENT_SEGMENT.length)
        }
        // Document ids look like "primary:Documents/vault.kdbx", drop the root id
        val rootSeparator = path.indexOf(':')
        if (rootSeparator >= 0 && path.indexOf('/') > rootSeparator) {
            path = path.substring(rootSeparator + 1)
        }
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null
        return segments.dropLast(1).takeLast(maxSegments).joinToString("/")
    }

    /**
     * Text shown under a database name when another database in the list has the same name.
     */
    fun describe(decodedPath: String?, lastModified: String?): String? {
        val parts = listOfNotNull(
            parentFolder(decodedPath),
            lastModified?.takeIf { it.isNotBlank() }
        )
        return if (parts.isEmpty()) decodedPath?.takeIf { it.isNotBlank() } else parts.joinToString(" · ")
    }

    /**
     * Whether [alias] is used by more than one database in [aliases] (case and spaces ignored).
     */
    fun isNameShared(alias: String?, aliases: List<String?>): Boolean {
        val key = alias?.trim()?.lowercase() ?: return false
        if (key.isEmpty()) return false
        return aliases.count { it?.trim()?.lowercase() == key } > 1
    }
}
