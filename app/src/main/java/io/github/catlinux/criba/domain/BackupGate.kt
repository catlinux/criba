package io.github.catlinux.criba.domain

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

sealed interface GateResult {
    data class Unlocked(val record: BackupRecord) : GateResult
    data class Locked(val reason: GateReason) : GateResult
}

enum class GateReason {
    NO_BACKUP,
    SOURCE_CHANGED,
    SOURCE_HASH_DIFFERS,
    DESTINATION_MISSING,
    DESTINATION_HASH_DIFFERS,
}

/** Puerta de seguridad: solo desbloquea multimedia con copia verificada y re-verificada ahora. */
class BackupGate(
    private val transport: BackupTransport,
    private val index: BackupIndex,
) {
    suspend fun hasRecord(file: ScanFile, sourcePath: String): Boolean {
        val record = index.find(sourcePath) ?: return false
        return record.sizeBytes == file.sizeBytes && record.modifiedEpochSeconds == file.modifiedEpochSeconds
    }

    suspend fun reverify(file: ScanFile, sourcePath: String): GateResult {
        val record = index.find(sourcePath) ?: return GateResult.Locked(GateReason.NO_BACKUP)
        if (record.sizeBytes != file.sizeBytes || record.modifiedEpochSeconds != file.modifiedEpochSeconds) {
            return GateResult.Locked(GateReason.SOURCE_CHANGED)
        }

        coroutineContext.ensureActive()
        val sourceHash = transport.sha256OfSource(sourcePath, file.sizeBytes)
        if (sourceHash == null || sourceHash != record.sha256) {
            return GateResult.Locked(GateReason.SOURCE_HASH_DIFFERS)
        }

        coroutineContext.ensureActive()
        val destinationSize = transport.sizeOfDestination(record.destinationRef)
        val destinationHash = transport.sha256OfDestination(record.destinationRef)
        if (destinationSize == null || destinationHash == null) {
            return GateResult.Locked(GateReason.DESTINATION_MISSING)
        }
        if (destinationSize != record.sizeBytes || destinationHash != record.sha256) {
            return GateResult.Locked(GateReason.DESTINATION_HASH_DIFFERS)
        }

        return GateResult.Unlocked(record)
    }

    suspend fun unlockedFiles(items: List<BackupItem>): Set<String> =
        items
            .filter { reverify(it.file, it.sourcePath) is GateResult.Unlocked }
            .map { it.sourcePath }
            .toSet()
}
