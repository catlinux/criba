package io.github.catlinux.criba.domain

/**
 * Permiso de borrado no eludible: solo puede crearse desde la confirmación explícita del usuario
 * con la lista exacta de archivos y los bytes reales que se le mostraron.
 */
class ConfirmedDeletion private constructor(
    val items: List<DeletionCandidate>,
    val totalBytes: Long,
) {
    companion object {
        fun confirm(items: List<DeletionCandidate>, shownTotalBytes: Long): ConfirmedDeletion? {
            if (items.isEmpty()) return null
            if (items.any { DeletionPolicy.evaluate(it) != DeletionVerdict.Allowed }) return null
            if (items.map { it.path }.toSet().size != items.size) return null
            val totalBytes = items.sumOf { it.sizeBytes }
            if (shownTotalBytes != totalBytes) return null
            return ConfirmedDeletion(items = items.toList(), totalBytes = totalBytes)
        }
    }
}
