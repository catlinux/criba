package io.github.catlinux.criba.data

import io.github.catlinux.criba.domain.ProtectedPaths
import io.github.catlinux.criba.domain.ScanFile
import java.io.File
import java.io.IOException

/** Recorre Descargas en modo lectura, omitiendo rutas protegidas y las que escapan de la raíz. */
fun walkDownloads(
    root: File,
    storageRoot: File,
    isProtected: (String) -> Boolean = ProtectedPaths::isProtected,
): List<ScanFile> {
    if (!root.exists() || !root.isDirectory) return emptyList()
    val rootPath = root.canonicalPathOrNull() ?: return emptyList()
    if (isProtected(rootPath)) return emptyList()
    val storagePath = storageRoot.canonicalPathOrNull()
    val rootPrefix = rootPath.trimEnd(File.separatorChar) + File.separator
    val files = mutableListOf<ScanFile>()

    fun walk(directory: File) {
        val entries = directory.listFiles() ?: return
        for (entry in entries) {
            val path = entry.canonicalPathOrNull() ?: continue
            if (!path.startsWith(rootPrefix)) continue
            if (isProtected(path)) continue
            if (!entry.canRead()) continue
            if (entry.isDirectory) {
                walk(entry)
            } else if (entry.isFile) {
                files += ScanFile(
                    id = path,
                    displayName = entry.name,
                    sizeBytes = entry.length(),
                    modifiedEpochSeconds = entry.lastModified() / 1000,
                    relativePath = relativeDirectory(path, storagePath),
                )
            }
        }
    }

    walk(root)
    return files
}

private fun File.canonicalPathOrNull(): String? =
    try {
        canonicalPath
    } catch (e: IOException) {
        null
    }

private fun relativeDirectory(path: String, storagePath: String?): String? {
    if (storagePath == null) return null
    val parent = File(path).parent ?: return null
    val base = storagePath.trimEnd(File.separatorChar)
    if (parent == base) return "/"
    if (!parent.startsWith(base + File.separator)) return null
    return parent.substring(base.length + 1).replace(File.separatorChar, '/') + "/"
}
