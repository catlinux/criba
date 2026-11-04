package io.github.catlinux.criba.data.duplicates

import io.github.catlinux.criba.domain.ContentHasher
import io.github.catlinux.criba.domain.DeletionPolicy
import io.github.catlinux.criba.domain.DeletionVerdict
import io.github.catlinux.criba.domain.ScanFile
import io.github.catlinux.criba.domain.toDeletionCandidate
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FileContentHasher @Inject constructor(
    @param:Named("storageRoot") private val storageRoot: String,
) : ContentHasher {
    override suspend fun sha256(file: ScanFile): String? = withContext(Dispatchers.IO) {
        val candidate = file.toDeletionCandidate(storageRoot) ?: return@withContext null
        if (DeletionPolicy.evaluate(candidate) is DeletionVerdict.Protected) return@withContext null
        hashPath(candidate.path, file.sizeBytes)
    }

    internal suspend fun hashPath(path: String, expectedSize: Long): String? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            try {
                if (hasSymlinkInPath(file)) return@withContext null
                if (!file.isFile) return@withContext null
                if (file.length() != expectedSize) return@withContext null

                val digest = MessageDigest.getInstance("SHA-256")
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().toHex()
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }

    private fun hasSymlinkInPath(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) return true
        var parent = file.absoluteFile.parentFile
        while (parent != null) {
            if (Files.isSymbolicLink(parent.toPath())) return true
            parent = parent.parentFile
        }
        return try {
            val canonical = file.canonicalPath
            val absolute = file.absoluteFile.normalize().path
            // En Windows las rutas no distinguen mayúsculas; en el resto, una diferencia delata un enlace.
            !canonical.equals(absolute, ignoreCase = File.separatorChar == '\\')
        } catch (e: IOException) {
            true
        }
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
