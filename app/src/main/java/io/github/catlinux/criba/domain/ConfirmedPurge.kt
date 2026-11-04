package io.github.catlinux.criba.domain

/**
 * Permiso no eludible para vaciar definitivamente la papelera: solo puede crearse desde la
 * confirmación explícita del usuario, con los elementos y los bytes reales que se le mostraron.
 */
class ConfirmedPurge private constructor(
    val items: List<TrashItem>,
    val totalBytes: Long,
) {
    companion object {
        fun confirm(items: List<TrashItem>, shownTotalBytes: Long): ConfirmedPurge? {
            if (items.isEmpty()) return null
            if (items.any { it.state != TrashState.IN_TRASH }) return null
            if (items.map { it.id }.toSet().size != items.size) return null
            val totalBytes = items.sumOf { it.bytes }
            if (shownTotalBytes != totalBytes) return null
            return ConfirmedPurge(items = items.toList(), totalBytes = totalBytes)
        }
    }
}
