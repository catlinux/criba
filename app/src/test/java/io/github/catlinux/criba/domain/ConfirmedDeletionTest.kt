package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmedDeletionTest {
    private fun candidate(path: String, sizeBytes: Long = 100) = DeletionCandidate(
        path = path,
        displayName = path.substringAfterLast('/'),
        sizeBytes = sizeBytes,
        modifiedEpochSeconds = 0,
    )

    private fun allowed(path: String, sizeBytes: Long = 100) =
        candidate("/storage/emulated/0/Download/$path", sizeBytes)

    @Test
    fun `confirma una lista valida con sus items y su total`() {
        val items = listOf(allowed("a.zip", 300), allowed("b.apk", 200))

        val confirmed = ConfirmedDeletion.confirm(items, shownTotalBytes = 500)

        assertNotNull(confirmed)
        assertEquals(items, confirmed?.items)
        assertEquals(500L, confirmed?.totalBytes)
    }

    @Test
    fun `no confirma una lista vacia`() {
        assertNull(ConfirmedDeletion.confirm(emptyList(), shownTotalBytes = 0))
    }

    @Test
    fun `no confirma si hay un elemento multimedia`() {
        val items = listOf(allowed("a.zip", 100), allowed("foto.jpg", 100))

        assertNull(ConfirmedDeletion.confirm(items, shownTotalBytes = 200))
    }

    @Test
    fun `no confirma si hay un elemento protegido`() {
        val items = listOf(allowed("a.zip", 100), candidate("/storage/emulated/0/Download/datos.db", 100))

        assertNull(ConfirmedDeletion.confirm(items, shownTotalBytes = 200))
    }

    @Test
    fun `no confirma si hay rutas duplicadas`() {
        val items = listOf(allowed("a.zip", 100), allowed("a.zip", 100))

        assertNull(ConfirmedDeletion.confirm(items, shownTotalBytes = 200))
    }

    @Test
    fun `no confirma si los bytes mostrados son mayores que la suma`() {
        val items = listOf(allowed("a.zip", 100))

        assertNull(ConfirmedDeletion.confirm(items, shownTotalBytes = 101))
    }

    @Test
    fun `no confirma si los bytes mostrados son menores que la suma`() {
        val items = listOf(allowed("a.zip", 100))

        assertNull(ConfirmedDeletion.confirm(items, shownTotalBytes = 99))
    }

    @Test
    fun `la lista devuelta no cambia si se muta la original`() {
        val original = mutableListOf(allowed("a.zip", 100), allowed("b.apk", 200))

        val confirmed = ConfirmedDeletion.confirm(original, shownTotalBytes = 300)
        original.clear()

        assertEquals(2, confirmed?.items?.size)
        assertEquals(300L, confirmed?.totalBytes)
    }

    @Test
    fun `suma varios elementos incluido uno de tamano cero`() {
        val items = listOf(allowed("a.zip", 250), allowed("vacio.txt", 0), allowed("b.apk", 50))

        val confirmed = ConfirmedDeletion.confirm(items, shownTotalBytes = 300)

        assertEquals(300L, confirmed?.totalBytes)
        assertEquals(3, confirmed?.items?.size)
    }
}
