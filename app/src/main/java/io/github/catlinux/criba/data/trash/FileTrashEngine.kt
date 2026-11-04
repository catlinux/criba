package io.github.catlinux.criba.data.trash

import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.DeletionCandidate
import io.github.catlinux.criba.domain.MoveToTrashResult
import io.github.catlinux.criba.domain.ProtectedPaths
import io.github.catlinux.criba.domain.PurgeResult
import io.github.catlinux.criba.domain.RestoreResult
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.domain.TrashFailureItem
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState
import io.github.catlinux.criba.domain.TrashedFile
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Motor de la papelera. Solo mueve dentro del mismo volumen con rename atómico; el único borrado
 * definitivo ocurre en [purgeItems] y siempre dentro de `trashRoot/<id>`.
 */
internal class FileTrashEngine(
    private val trashRoot: File?,
    private val log: ActionLog,
    private val index: TrashIndex,
    private val isProtected: (String) -> Boolean = ProtectedPaths::isProtected,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun moveToTrash(items: List<DeletionCandidate>): MoveToTrashResult = withContext(Dispatchers.IO) {
        val moved = mutableListOf<TrashedFile>()
        val failed = mutableListOf<TrashFailureItem>()
        val root = trashRoot

        for (candidate in items) {
            val id = newId()
            val logId = log.begin(ActionType.MOVED_TO_TRASH, candidate.path, id, candidate.sizeBytes)

            suspend fun fail(failure: TrashFailure, detail: String? = null) {
                log.complete(logId, ActionResult.FAILED, detail ?: failure.name)
                failed += TrashFailureItem(candidate.path, failure, detail)
            }

            if (root == null) {
                fail(TrashFailure.TRASH_UNAVAILABLE)
                continue
            }

            val original = File(candidate.path)
            if (hasSymlinkInPath(original)) {
                fail(TrashFailure.SYMLINK)
                continue
            }
            val canonical = try {
                original.canonicalPath
            } catch (e: IOException) {
                fail(TrashFailure.IO_ERROR, e.message)
                continue
            }
            if (isProtected(canonical)) {
                fail(TrashFailure.PROTECTED)
                continue
            }
            if (!original.exists()) {
                fail(TrashFailure.MISSING)
                continue
            }
            if (!original.isFile) {
                fail(TrashFailure.NOT_A_REGULAR_FILE)
                continue
            }
            if (original.length() != candidate.sizeBytes ||
                original.lastModified() / 1000 != candidate.modifiedEpochSeconds
            ) {
                fail(TrashFailure.CHANGED_SINCE_SCAN)
                continue
            }

            val idDirectory = File(root, id)
            if (!idDirectory.isDirectory && !idDirectory.mkdirs()) {
                fail(TrashFailure.TRASH_UNAVAILABLE, "no se pudo crear la carpeta de la papelera")
                continue
            }
            // La papelera está en la raíz del almacenamiento: que ningún indexador de medios la recorra.
            try {
                File(root, ".nomedia").createNewFile()
            } catch (_: IOException) {
            }
            val destination = File(idDirectory, original.name)
            if (destination.exists()) {
                removeEmptyTrashDirectory(idDirectory)
                fail(TrashFailure.TARGET_EXISTS)
                continue
            }

            try {
                Files.move(original.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                removeEmptyTrashDirectory(idDirectory)
                fail(TrashFailure.DIFFERENT_VOLUME, e.message)
                continue
            } catch (e: IOException) {
                removeEmptyTrashDirectory(idDirectory)
                fail(TrashFailure.IO_ERROR, e.message)
                continue
            }

            if (original.exists() || !destination.isFile || destination.length() != candidate.sizeBytes) {
                val returned = try {
                    if (destination.exists()) {
                        Files.move(destination.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    }
                    true
                } catch (e: IOException) {
                    false
                }
                removeEmptyTrashDirectory(idDirectory)
                fail(
                    TrashFailure.VERIFY_FAILED,
                    if (returned) "verificación fallida; devuelto al origen" else "verificación fallida; no se pudo devolver",
                )
                continue
            }

            try {
                index.add(
                    TrashItem(
                        id = id,
                        originalPath = candidate.path,
                        trashPath = destination.path,
                        bytes = candidate.sizeBytes,
                        sha256 = null,
                        trashedAtEpochMillis = nowEpochMillis(),
                        state = TrashState.IN_TRASH,
                    ),
                )
            } catch (e: Exception) {
                val returned = try {
                    if (destination.exists()) {
                        Files.move(destination.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    }
                    true
                } catch (e2: IOException) {
                    false
                }
                removeEmptyTrashDirectory(idDirectory)
                fail(
                    TrashFailure.IO_ERROR,
                    if (returned) "no se pudo registrar en el índice; devuelto al origen" else "no se pudo registrar en el índice",
                )
                continue
            }

            log.complete(logId, ActionResult.SUCCESS)
            moved += TrashedFile(trashId = id, originalPath = candidate.path, bytes = candidate.sizeBytes)
        }

        MoveToTrashResult(moved = moved, failed = failed)
    }

    suspend fun restore(trashId: String): RestoreResult = withContext(Dispatchers.IO) {
        val item = index.get(trashId)
            ?: return@withContext RestoreResult(null, false, TrashFailure.NOT_IN_TRASH, null)

        val logId = log.begin(ActionType.RESTORED, item.originalPath, item.id, item.bytes)

        suspend fun fail(failure: TrashFailure, detail: String? = null): RestoreResult {
            log.complete(logId, ActionResult.FAILED, detail ?: failure.name)
            return RestoreResult(null, false, failure, detail)
        }

        if (item.state != TrashState.IN_TRASH) return@withContext fail(TrashFailure.NOT_IN_TRASH)
        val root = trashRoot ?: return@withContext fail(TrashFailure.TRASH_UNAVAILABLE)

        val trashFile = File(item.trashPath)
        if (!isInsideTrash(trashFile, root)) return@withContext fail(TrashFailure.OUTSIDE_TRASH)
        if (Files.isSymbolicLink(trashFile.toPath())) {
            return@withContext fail(TrashFailure.VERIFY_FAILED, "la papelera contiene un enlace simbólico")
        }
        if (!trashFile.exists()) return@withContext fail(TrashFailure.MISSING)
        if (!trashFile.isFile) return@withContext fail(TrashFailure.VERIFY_FAILED, "no es un archivo regular")
        if (trashFile.length() != item.bytes) {
            return@withContext fail(TrashFailure.VERIFY_FAILED, "tamaño distinto al del índice")
        }

        val destination = File(item.originalPath)
        if (isProtected(destination.path)) return@withContext fail(TrashFailure.PROTECTED)

        val parent = destination.absoluteFile.parentFile
            ?: return@withContext fail(TrashFailure.IO_ERROR, "ruta de destino sin carpeta")
        if (!parent.exists()) {
            if (isProtected(parent.path)) return@withContext fail(TrashFailure.PROTECTED)
            if (!parent.mkdirs()) return@withContext fail(TrashFailure.IO_ERROR, "no se pudo crear la carpeta")
        }

        val unique = uniqueDestination(destination) ?: return@withContext fail(TrashFailure.TARGET_EXISTS)
        val finalDestination = unique.first
        val renamed = unique.second

        try {
            Files.move(trashFile.toPath(), finalDestination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            return@withContext fail(TrashFailure.DIFFERENT_VOLUME, e.message)
        } catch (e: IOException) {
            return@withContext fail(TrashFailure.IO_ERROR, e.message)
        }

        if (!finalDestination.isFile || finalDestination.length() != item.bytes || trashFile.exists()) {
            try {
                if (finalDestination.exists()) {
                    Files.move(finalDestination.toPath(), trashFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
                }
            } catch (e: IOException) {
                // Se deja el detalle en el registro; el archivo sigue en la papelera.
            }
            return@withContext fail(TrashFailure.VERIFY_FAILED)
        }

        index.setState(item.id, TrashState.RESTORED)
        removeEmptyTrashDirectory(trashFile.absoluteFile.parentFile)
        log.complete(logId, ActionResult.SUCCESS)
        RestoreResult(restoredPath = finalDestination.path, renamed = renamed, failure = null, detail = null)
    }

    /** Único punto donde se borra definitivamente, y solo archivos dentro de `trashRoot/<id>`. */
    suspend fun purgeItems(items: List<TrashItem>): PurgeResult = withContext(Dispatchers.IO) {
        val purged = mutableListOf<TrashedFile>()
        val failed = mutableListOf<TrashFailureItem>()
        val root = trashRoot

        for (item in items) {
            val logId = log.begin(ActionType.PURGED, item.originalPath, item.id, item.bytes)

            suspend fun fail(failure: TrashFailure, detail: String? = null) {
                log.complete(logId, ActionResult.FAILED, detail ?: failure.name)
                failed += TrashFailureItem(item.originalPath, failure, detail)
            }

            if (root == null) {
                fail(TrashFailure.TRASH_UNAVAILABLE)
                continue
            }
            if (item.state != TrashState.IN_TRASH) {
                fail(TrashFailure.NOT_IN_TRASH)
                continue
            }

            val file = File(item.trashPath)
            if (!isInsideIdDirectory(file, root, item.id)) {
                fail(TrashFailure.OUTSIDE_TRASH)
                continue
            }
            if (Files.isSymbolicLink(file.toPath())) {
                fail(TrashFailure.OUTSIDE_TRASH, "enlace simbólico")
                continue
            }

            var detail: String? = null
            if (file.exists()) {
                if (!file.isFile) {
                    fail(TrashFailure.IO_ERROR, "no es un archivo regular")
                    continue
                }
                if (!file.delete()) {
                    fail(TrashFailure.IO_ERROR, "no se pudo borrar")
                    continue
                }
                if (file.exists()) {
                    fail(TrashFailure.IO_ERROR, "sigue existiendo tras borrar")
                    continue
                }
            } else {
                detail = "ya no estaba"
            }

            index.setState(item.id, TrashState.PURGED)
            removeEmptyTrashDirectory(file.absoluteFile.parentFile)
            log.complete(logId, ActionResult.SUCCESS, detail)
            purged += TrashedFile(trashId = item.id, originalPath = item.originalPath, bytes = item.bytes)
        }

        PurgeResult(purged = purged, failed = failed)
    }

    suspend fun purgeExpired(nowEpochMillis: Long, retentionDays: Int = 30): PurgeResult {
        require(retentionDays >= 1) { "retentionDays debe ser al menos 1" }
        return withContext(Dispatchers.IO) {
            val threshold = nowEpochMillis - retentionDays.toLong() * MILLIS_PER_DAY
            purgeItems(index.inTrash().filter { it.trashedAtEpochMillis <= threshold })
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

    /** Compara con separador final para no aceptar prefijos engañosos como `trashX`. */
    private fun isInsideTrash(file: File, root: File): Boolean =
        try {
            val rootPrefix = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
            file.canonicalPath.startsWith(rootPrefix)
        } catch (e: IOException) {
            false
        }

    /** El vaciado solo borra lo que está dentro de `trashRoot/<id>`. */
    private fun isInsideIdDirectory(file: File, root: File, id: String): Boolean =
        try {
            val idPrefix = File(root, id).canonicalPath.trimEnd(File.separatorChar) + File.separator
            file.canonicalPath.startsWith(idPrefix)
        } catch (e: IOException) {
            false
        }

    private fun uniqueDestination(destination: File): Pair<File, Boolean>? {
        if (!destination.exists()) return destination to false

        val parent = destination.absoluteFile.parentFile ?: return null
        val name = destination.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""

        for (attempt in 1..100) {
            val suffix = if (attempt == 1) " (restaurado)" else " (restaurado $attempt)"
            val candidate = File(parent, "$base$suffix$extension")
            if (!candidate.exists()) return candidate to true
        }
        return null
    }

    private fun removeEmptyTrashDirectory(directory: File?) {
        if (directory == null) return
        if (directory.isDirectory && directory.listFiles()?.isEmpty() == true) directory.delete()
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
