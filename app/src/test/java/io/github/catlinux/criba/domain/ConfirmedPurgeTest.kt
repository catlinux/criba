package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConfirmedPurgeTest {
    private fun item(id: String, bytes: Long = 100, state: TrashState = TrashState.IN_TRASH) = TrashItem(
        id = id,
        originalPath = "/storage/emulated/0/Download/$id",
        trashPath = "/storage/emulated/0/.criba-trash/$id",
        bytes = bytes,
        sha256 = null,
        trashedAtEpochMillis = 0,
        state = state,
    )

    @Test
    fun `confirma una lista valida con sus items y su total`() {
        val items = listOf(item("a", 300), item("b", 200))

        val confirmed = ConfirmedPurge.confirm(items, shownTotalBytes = 500)

        assertNotNull(confirmed)
        assertEquals(items, confirmed?.items)
        assertEquals(500L, confirmed?.totalBytes)
    }

    @Test
    fun `no confirma una lista vacia`() {
        assertNull(ConfirmedPurge.confirm(emptyList(), shownTotalBytes = 0))
    }

    @Test
    fun `no confirma si algun elemento no esta en la papelera`() {
        val items = listOf(item("a"), item("b", state = TrashState.RESTORED))

        assertNull(ConfirmedPurge.confirm(items, shownTotalBytes = 200))
    }

    @Test
    fun `no confirma si hay ids repetidos`() {
        val items = listOf(item("a"), item("a"))

        assertNull(ConfirmedPurge.confirm(items, shownTotalBytes = 200))
    }

    @Test
    fun `no confirma si los bytes mostrados no coinciden`() {
        assertNull(ConfirmedPurge.confirm(listOf(item("a")), shownTotalBytes = 99))
        assertNull(ConfirmedPurge.confirm(listOf(item("a")), shownTotalBytes = 101))
    }

    @Test
    fun `la lista devuelta no cambia si se muta la original`() {
        val original = mutableListOf(item("a", 100), item("b", 200))

        val confirmed = ConfirmedPurge.confirm(original, shownTotalBytes = 300)
        original.clear()

        assertEquals(2, confirmed?.items?.size)
        assertEquals(300L, confirmed?.totalBytes)
    }

    @Test
    fun `suma varios elementos incluido uno de tamano cero`() {
        val items = listOf(item("a", 250), item("vacio", 0), item("b", 50))

        val confirmed = ConfirmedPurge.confirm(items, shownTotalBytes = 300)

        assertEquals(300L, confirmed?.totalBytes)
        assertEquals(3, confirmed?.items?.size)
    }

    @Test
    fun `rechaza bytes negativos en un elemento`() {
        assertThrows(IllegalArgumentException::class.java) { item("a", bytes = -1) }
    }
}
