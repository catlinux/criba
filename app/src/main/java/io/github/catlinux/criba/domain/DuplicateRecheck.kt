package io.github.catlinux.criba.domain

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Verifica justo antes de borrar que la copia conservada y cada copia a borrar siguen teniendo
 * el mismo contenido; si la conservada no se puede verificar, no se confirma nada.
 */
class DuplicateRecheck(private val hasher: ContentHasher) {
    suspend fun confirmedDeletions(selection: DuplicateSelection): List<ScanFile> {
        coroutineContext.ensureActive()
        val keptHash = hasher.sha256(selection.keptFile) ?: return emptyList()
        if (keptHash != selection.group.sha256) return emptyList()

        val confirmed = mutableListOf<ScanFile>()
        for (file in selection.toDelete) {
            coroutineContext.ensureActive()
            if (hasher.sha256(file) == keptHash) confirmed += file
        }
        return confirmed
    }
}
