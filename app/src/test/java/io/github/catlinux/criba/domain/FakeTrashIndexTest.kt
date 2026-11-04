package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeTrashIndexTest {
    private fun item(id: String, bytes: Long = 100, trashedAt: Long = 0) = TrashItem(
        id = id,
        originalPath = "/storage/emulated/0/Download/$id",
        trashPath = "/storage/emulated/0/Android/data/io.github.catlinux.criba/files/trash/$id",
        bytes = bytes,
        sha256 = null,
        trashedAtEpochMillis = trashedAt,
        state = TrashState.IN_TRASH,
    )

    @Test
    fun `add guarda y devuelve el elemento`() = runSuspend {
        val index = FakeTrashIndex()
        index.add(item("a"))

        assertEquals("a", index.get("a")?.id)
    }

    @Test
    fun `add con id duplicado lanza IllegalStateException`() {
        assertThrows(IllegalStateException::class.java) {
            runSuspend {
                val index = FakeTrashIndex()
                index.add(item("a"))
                index.add(item("a"))
            }
        }
    }

    @Test
    fun `inTrashBytes suma solo los que estan en la papelera`() = runSuspend {
        val index = FakeTrashIndex()
        index.add(item("a", bytes = 100))
        index.add(item("b", bytes = 200))
        index.setState("b", TrashState.RESTORED)

        assertEquals(100L, index.inTrashBytes())
        assertEquals(listOf("a"), index.inTrash().map { it.id })
    }

    @Test
    fun `inTrash devuelve los mas antiguos primero`() = runSuspend {
        val index = FakeTrashIndex()
        index.add(item("nuevo", trashedAt = 20))
        index.add(item("viejo", trashedAt = 10))

        assertEquals(listOf("viejo", "nuevo"), index.inTrash().map { it.id })
    }

    @Test
    fun `setState cambia el estado del elemento`() = runSuspend {
        val index = FakeTrashIndex()
        index.add(item("a"))

        index.setState("a", TrashState.PURGED)

        assertEquals(TrashState.PURGED, index.get("a")?.state)
        assertEquals(0L, index.inTrashBytes())
    }
}
