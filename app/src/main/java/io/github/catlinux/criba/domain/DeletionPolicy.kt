package io.github.catlinux.criba.domain

data class DeletionCandidate(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
) {
    init {
        require(sizeBytes >= 0) { "sizeBytes no puede ser negativo" }
    }
}

sealed interface DeletionVerdict {
    object Allowed : DeletionVerdict
    object RequiresVerifiedBackup : DeletionVerdict
    data class Protected(val reason: ProtectedReason) : DeletionVerdict
}

enum class ProtectedReason { PROTECTED_PATH, NOT_ABSOLUTE, INSIDE_TRASH }

object DeletionPolicy {
    const val TRASH_SEGMENT = "/Android/data/io.github.catlinux.criba/files/trash/"

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw",
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v",
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr",
    )

    fun evaluate(candidate: DeletionCandidate): DeletionVerdict {
        val path = candidate.path
        if (!path.startsWith("/")) return DeletionVerdict.Protected(ProtectedReason.NOT_ABSOLUTE)
        if (isInsideTrash(path)) return DeletionVerdict.Protected(ProtectedReason.INSIDE_TRASH)
        if (ProtectedPaths.isProtected(path)) return DeletionVerdict.Protected(ProtectedReason.PROTECTED_PATH)
        return if (mediaExtension(path)?.let { it in MEDIA_EXTENSIONS } == true) {
            DeletionVerdict.RequiresVerifiedBackup
        } else {
            DeletionVerdict.Allowed
        }
    }

    private fun isInsideTrash(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.contains(TRASH_SEGMENT, ignoreCase = true)) return true
        return normalized.trimEnd('/').endsWith("/trash", ignoreCase = true)
    }

    /** Extensión tras el último punto del último segmento; null si no hay punto o no hay nada detrás. */
    private fun mediaExtension(path: String): String? {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        if (dot < 0) return null
        return name.substring(dot + 1).lowercase().ifEmpty { null }
    }
}
