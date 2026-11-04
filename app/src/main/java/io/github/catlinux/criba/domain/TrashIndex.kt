package io.github.catlinux.criba.domain

enum class TrashState { IN_TRASH, RESTORED, PURGED }

data class TrashItem(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val bytes: Long,
    val sha256: String?,
    val trashedAtEpochMillis: Long,
    val state: TrashState,
) {
    init {
        require(bytes >= 0) { "bytes no puede ser negativo" }
    }
}

interface TrashIndex {
    /** Un id de papelera nunca se reutiliza: si ya existe, lanza IllegalStateException. */
    suspend fun add(item: TrashItem)

    suspend fun setState(id: String, state: TrashState)

    suspend fun get(id: String): TrashItem?

    suspend fun inTrash(): List<TrashItem>

    suspend fun inTrashBytes(): Long
}
