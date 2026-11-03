package io.github.catlinux.criba.domain

/** Traduce un documentId SAF de almacenamiento externo a una ruta absoluta del sistema. */
object SafPaths {
    private val VOLUME_ID = Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")

    fun documentIdToPath(documentId: String): String? {
        val separator = documentId.indexOf(':')
        if (separator < 0) return null

        val volume = documentId.substring(0, separator)
        val relative = documentId.substring(separator + 1)
        val base = when {
            volume == "primary" -> "/storage/emulated/0"
            VOLUME_ID.matches(volume) -> "/storage/$volume"
            else -> return null
        }
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}
