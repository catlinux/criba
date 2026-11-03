package io.github.catlinux.criba.domain

/**
 * Punto único de decisión sobre rutas que jamás se deben tocar (sistema, datos de otras apps,
 * bases de datos y claves). Las rutas no reconocidas se consideran protegidas por prudencia.
 */
object ProtectedPaths {
    private val VOLUME_ID = Regex("[0-9a-fA-F]{4}-[0-9a-fA-F]{4}")

    private val PROTECTED_EXTENSIONS = listOf(
        ".db",
        ".sqlite",
        ".sqlite3",
        ".db-wal",
        ".db-shm",
        ".db-journal",
        ".jks",
        ".keystore",
        ".bks",
        ".p12",
        ".pfx",
        ".pem",
        ".key",
    )

    fun isProtected(path: String): Boolean {
        if (path.isBlank()) return true
        val normalized = path.replace('\\', '/')
        if (!normalized.startsWith("/")) return true

        val segments = resolveSegments(normalized) ?: return true
        val rootLength = allowedRootLength(segments) ?: return true
        val rest = segments.drop(rootLength)
        if (rest.isEmpty()) return false

        if (rest[0].equals("android", ignoreCase = true) &&
            rest.size >= 2 &&
            (rest[1].equals("data", ignoreCase = true) || rest[1].equals("obb", ignoreCase = true))
        ) {
            return true
        }
        if (rest.any { it.equals("databases", ignoreCase = true) || it == ".ssh" || it == ".gnupg" }) {
            return true
        }
        val last = rest.last().lowercase()
        return PROTECTED_EXTENSIONS.any { last.endsWith(it) }
    }

    /** Devuelve los segmentos ya resueltos, o null si un '..' intenta subir por encima de la raíz. */
    private fun resolveSegments(path: String): List<String>? {
        val stack = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return stack.toList()
    }

    /** Número de segmentos de la raíz permitida, o null si la ruta no está bajo ninguna. */
    private fun allowedRootLength(segments: List<String>): Int? {
        if (segments.isEmpty()) return null
        if (segments[0].equals("sdcard", ignoreCase = true)) return 1

        if (!segments[0].equals("storage", ignoreCase = true) || segments.size < 2) return null
        val second = segments[1]
        if (second.equals("emulated", ignoreCase = true)) {
            val volume = segments.getOrNull(2) ?: return null
            return if (volume.isNotEmpty() && volume.all { it.isDigit() }) 3 else null
        }
        return if (VOLUME_ID.matches(second)) 2 else null
    }
}
