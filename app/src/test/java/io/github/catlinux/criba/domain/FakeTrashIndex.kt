package io.github.catlinux.criba.domain

/** Implementación en memoria del índice de la papelera para tests y pasos futuros de la UI. */
class FakeTrashIndex : TrashIndex {
    private val items = LinkedHashMap<String, TrashItem>()

    override suspend fun add(item: TrashItem) {
        check(items.putIfAbsent(item.id, item) == null) { "ya existe un elemento con el id ${item.id}" }
    }

    override suspend fun setState(id: String, state: TrashState) {
        val current = items[id] ?: return
        items[id] = current.copy(state = state)
    }

    override suspend fun get(id: String): TrashItem? = items[id]

    override suspend fun inTrash(): List<TrashItem> =
        items.values
            .filter { it.state == TrashState.IN_TRASH }
            .sortedBy { it.trashedAtEpochMillis }

    override suspend fun inTrashBytes(): Long =
        items.values.filter { it.state == TrashState.IN_TRASH }.sumOf { it.bytes }
}
