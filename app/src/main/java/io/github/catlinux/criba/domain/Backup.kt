package io.github.catlinux.criba.domain

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

data class BackupRecord(
    val sourcePath: String,
    val sizeBytes: Long,
    val modifiedEpochSeconds: Long,
    val sha256: String,
    val destinationRef: String,
    val destinationLabel: String,
    val verifiedAtEpochSeconds: Long,
)

interface BackupIndex {
    suspend fun add(record: BackupRecord)

    suspend fun find(sourcePath: String): BackupRecord?
}

data class BackupCopyResult(
    val sha256: String,
    val bytesCopied: Long,
    val destinationRef: String,
)

interface BackupTransport {
    suspend fun existingNames(): Set<String>

    /** Copia en streaming y devuelve el SHA-256 del origen calculado mientras copia; null si falló. */
    suspend fun copyFile(
        file: ScanFile,
        sourcePath: String,
        destinationName: String,
        onBytes: (Long) -> Unit,
    ): BackupCopyResult?

    /** Relee el destino desde cero: es la única prueba de que la copia vale. */
    suspend fun sha256OfDestination(destinationRef: String): String?

    suspend fun sizeOfDestination(destinationRef: String): Long?

    /** Solo para el archivo que esta copia acaba de crear y ha fallado la verificación. */
    suspend fun discardPartial(destinationRef: String)

    suspend fun sha256OfSource(sourcePath: String, expectedSize: Long): String?

    val label: String
}

enum class BackupFailure {
    SOURCE_UNREADABLE,
    COPY_FAILED,
    SIZE_MISMATCH,
    HASH_MISMATCH,
    DESTINATION_UNREADABLE,
    SOURCE_CHANGED,
    INDEX_FAILED,
}

sealed interface BackupOutcome {
    data class Verified(val record: BackupRecord) : BackupOutcome
    data class Failed(val failure: BackupFailure) : BackupOutcome
}

data class BackupItem(val file: ScanFile, val sourcePath: String)

data class BackupProgress(
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
)

data class BackupReport(val outcomes: Map<String, BackupOutcome>) {
    val verifiedCount: Int get() = outcomes.values.count { it is BackupOutcome.Verified }
    val failedCount: Int get() = outcomes.values.count { it is BackupOutcome.Failed }
    val verifiedBytes: Long
        get() = outcomes.values.filterIsInstance<BackupOutcome.Verified>().sumOf { it.record.sizeBytes }
}

/** Nombre libre dentro del destino, con sufijo antes de la extensión y sin distinguir mayúsculas. */
fun uniqueBackupName(name: String, existing: Set<String>): String {
    if (existing.none { it.equals(name, ignoreCase = true) }) return name

    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val extension = if (dot > 0) name.substring(dot) else ""
    var attempt = 1
    while (true) {
        val candidate = "$base ($attempt)$extension"
        if (existing.none { it.equals(candidate, ignoreCase = true) }) return candidate
        attempt++
    }
}

class BackupVerifier(
    private val transport: BackupTransport,
    private val index: BackupIndex,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    suspend fun backUp(
        items: List<BackupItem>,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupReport {
        val existing = transport.existingNames().toMutableSet()
        val outcomes = mutableMapOf<String, BackupOutcome>()
        val bytesTotal = items.sumOf { it.file.sizeBytes }
        var filesDone = 0
        var bytesDone = 0L

        for (item in items) {
            coroutineContext.ensureActive()
            val file = item.file
            val name = uniqueBackupName(file.displayName, existing)
            existing += name

            val copy = transport.copyFile(file, item.sourcePath, name) { copied ->
                onProgress(
                    BackupProgress(
                        filesDone = filesDone,
                        filesTotal = items.size,
                        bytesDone = (bytesDone + copied).coerceIn(0, bytesTotal),
                        bytesTotal = bytesTotal,
                    ),
                )
            }

            val outcome = when {
                copy == null -> {
                    val sourceHash = transport.sha256OfSource(item.sourcePath, file.sizeBytes)
                    BackupOutcome.Failed(
                        if (sourceHash == null) BackupFailure.SOURCE_UNREADABLE else BackupFailure.COPY_FAILED,
                    )
                }
                copy.bytesCopied != file.sizeBytes -> {
                    transport.discardPartial(copy.destinationRef)
                    BackupOutcome.Failed(BackupFailure.SIZE_MISMATCH)
                }
                else -> verifyCopy(item, copy)
            }

            outcomes[item.sourcePath] = outcome
            filesDone++
            bytesDone = (bytesDone + file.sizeBytes).coerceAtMost(bytesTotal)
            onProgress(BackupProgress(filesDone, items.size, bytesDone, bytesTotal))
        }

        return BackupReport(outcomes)
    }

    private suspend fun verifyCopy(item: BackupItem, copy: BackupCopyResult): BackupOutcome {
        val file = item.file
        val destinationSize = transport.sizeOfDestination(copy.destinationRef)
            ?: run {
                transport.discardPartial(copy.destinationRef)
                return BackupOutcome.Failed(BackupFailure.DESTINATION_UNREADABLE)
            }
        if (destinationSize != file.sizeBytes) {
            transport.discardPartial(copy.destinationRef)
            return BackupOutcome.Failed(BackupFailure.SIZE_MISMATCH)
        }

        val destinationHash = transport.sha256OfDestination(copy.destinationRef)
            ?: run {
                transport.discardPartial(copy.destinationRef)
                return BackupOutcome.Failed(BackupFailure.DESTINATION_UNREADABLE)
            }
        if (destinationHash != copy.sha256) {
            transport.discardPartial(copy.destinationRef)
            return BackupOutcome.Failed(BackupFailure.HASH_MISMATCH)
        }

        coroutineContext.ensureActive()
        val sourceHash = transport.sha256OfSource(item.sourcePath, file.sizeBytes)
        if (sourceHash == null || sourceHash != copy.sha256) {
            transport.discardPartial(copy.destinationRef)
            return BackupOutcome.Failed(BackupFailure.SOURCE_CHANGED)
        }

        val record = BackupRecord(
            sourcePath = item.sourcePath,
            sizeBytes = file.sizeBytes,
            modifiedEpochSeconds = file.modifiedEpochSeconds,
            sha256 = copy.sha256,
            destinationRef = copy.destinationRef,
            destinationLabel = transport.label,
            verifiedAtEpochSeconds = nowEpochSeconds(),
        )
        return try {
            index.add(record)
            BackupOutcome.Verified(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BackupOutcome.Failed(BackupFailure.INDEX_FAILED)
        }
    }
}

object BackupDestinationPolicy {
    fun accepts(destinationFilePath: String?, sourcePaths: List<String>): Boolean {
        val destination = destinationFilePath?.replace('\\', '/')?.trimEnd('/') ?: return true
        if (destination.contains(DeletionPolicy.TRASH_SEGMENT.trimEnd('/'), ignoreCase = true)) return false
        if (destination.endsWith("/${DeletionPolicy.TRASH_DIRECTORY_NAME}", ignoreCase = true)) return false

        for (source in sourcePaths) {
            val normalizedSource = source.replace('\\', '/')
            val parent = normalizedSource.substringBeforeLast('/', "")
            if (destination.equals(parent, ignoreCase = true)) return false
            if (isAncestor(destination, normalizedSource)) return false
        }
        return true
    }

    fun isInternalStorage(destinationFilePath: String?, storageRoot: String): Boolean {
        val destination = destinationFilePath?.replace('\\', '/')?.trimEnd('/') ?: return false
        val root = storageRoot.replace('\\', '/').trimEnd('/')
        return destination.startsWith(root)
    }

    private fun isAncestor(candidate: String, path: String): Boolean =
        path.equals(candidate, ignoreCase = true) || path.startsWith("$candidate/", ignoreCase = true)
}
