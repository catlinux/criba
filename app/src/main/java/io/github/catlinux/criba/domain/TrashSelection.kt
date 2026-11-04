package io.github.catlinux.criba.domain

object TrashRetention {
    const val DEFAULT_DAYS = 30
    private const val MILLIS_PER_DAY = 86_400_000L

    fun daysLeft(item: TrashItem, nowEpochMillis: Long, retentionDays: Int = DEFAULT_DAYS): Int {
        require(retentionDays >= 1) { "retentionDays debe ser al menos 1" }
        val expiry = item.trashedAtEpochMillis + retentionDays.toLong() * MILLIS_PER_DAY
        val remaining = expiry - nowEpochMillis
        if (remaining <= 0) return 0
        return ((remaining - 1) / MILLIS_PER_DAY + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

class TrashSelection(
    items: List<TrashItem>,
    selectedIds: Set<String> = emptySet(),
) {
    val items: List<TrashItem> = items.filter { it.state == TrashState.IN_TRASH }.toList()
    private val existingIds = this.items.map { it.id }.toSet()
    val selectedIds: Set<String> = selectedIds.filter { it in existingIds }.toSet()
    val selectedItems: List<TrashItem> = this.items.filter { it.id in this.selectedIds }
    val selectedBytes: Long get() = selectedItems.sumOf { it.bytes }
    val selectedCount: Int get() = selectedItems.size

    fun toggle(id: String): TrashSelection {
        if (id !in existingIds) return this
        val updated = selectedIds.toMutableSet()
        if (!updated.add(id)) updated.remove(id)
        return TrashSelection(items, updated)
    }

    fun selectAll(): TrashSelection = TrashSelection(items, selectedIds + existingIds)

    fun clear(): TrashSelection = TrashSelection(items)

    fun confirmPurge(): ConfirmedPurge? = ConfirmedPurge.confirm(selectedItems, selectedBytes)
}
