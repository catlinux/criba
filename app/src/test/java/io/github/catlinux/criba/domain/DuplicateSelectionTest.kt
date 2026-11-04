package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSelectionTest {
    private fun file(
        name: String,
        relativePath: String? = "Download/",
        modified: Long = 100,
        id: String = name,
    ) = ScanFile(id, name, 10, modified, relativePath)

    private fun group(vararg files: ScanFile) = DuplicateGroup("hash", files.toList(), 10)

    @Test
    fun `prefiere conservar una copia fuera de descargas y capturas`() {
        val download = file("a", "Download/")
        val pictures = file("b", "Pictures/", modified = 999)

        assertEquals(pictures.id, KeepPolicy.chooseKept(listOf(download, pictures)).id)
    }

    @Test
    fun `no conserva una copia de capturas si hay otra`() {
        val screenshot = file("a", "Pictures/Screenshots/", modified = 1)
        val documents = file("b", "Documents/", modified = 999)

        assertEquals(documents.id, KeepPolicy.chooseKept(listOf(screenshot, documents)).id)
    }

    @Test
    fun `sin copias preferidas conserva la mas antigua`() {
        val newer = file("a", "Download/", modified = 200)
        val older = file("b", "Download/", modified = 100)

        assertEquals(older.id, KeepPolicy.chooseKept(listOf(newer, older)).id)
    }

    @Test
    fun `entre preferidas conserva la mas antigua`() {
        val newer = file("a", "Documents/", modified = 200)
        val older = file("b", "Pictures/", modified = 100)

        assertEquals(older.id, KeepPolicy.chooseKept(listOf(newer, older)).id)
    }

    @Test
    fun `desempata por ruta y luego por nombre`() {
        val zeta = file("zeta", "Documents/", modified = 100)
        val alfa = file("alfa", "DCIM/", modified = 100)

        assertEquals(alfa.id, KeepPolicy.chooseKept(listOf(zeta, alfa)).id)
    }

    @Test
    fun `la seleccion conserva una copia y el resto van a borrar`() {
        val a = file("a")
        val b = file("b")
        val selection = DuplicateSelection(group(a, b), keptId = a.id)

        assertEquals(a.id, selection.keptId)
        assertEquals(listOf(b.id), selection.toDelete.map { it.id })
        assertEquals(10L, selection.reclaimableBytes)
    }

    @Test
    fun `nunca quedan todas las copias para borrar en grupos de dos o tres`() {
        val two = DuplicateSelection(group(file("a"), file("b")))
        val three = DuplicateSelection(group(file("a"), file("b"), file("c")))

        assertEquals(1, two.toDelete.size)
        assertEquals(2, three.toDelete.size)
        assertTrue(two.toDelete.none { it.id == two.keptId })
        assertTrue(three.toDelete.none { it.id == three.keptId })
    }

    @Test
    fun `withKept cambia la copia conservada`() {
        val a = file("a")
        val b = file("b")
        val selection = DuplicateSelection(group(a, b), keptId = a.id)

        val changed = selection.withKept(b.id)

        assertEquals(b.id, changed.keptId)
        assertEquals(listOf(a.id), changed.toDelete.map { it.id })
        assertNotEquals(selection.keptId, changed.keptId)
    }

    @Test
    fun `withKept rechaza un id que no esta en el grupo`() {
        val selection = DuplicateSelection(group(file("a"), file("b")))

        assertThrows(IllegalArgumentException::class.java) { selection.withKept("desconocido") }
    }

    @Test
    fun `el constructor rechaza una copia conservada ajena al grupo`() {
        assertThrows(IllegalArgumentException::class.java) {
            DuplicateSelection(group(file("a"), file("b")), keptId = "desconocido")
        }
    }

    @Test
    fun `la politica por defecto elige una copia del grupo`() {
        val selection = DuplicateSelection(group(file("a", "Download/"), file("b", "Documents/")))

        assertTrue(selection.group.files.any { it.id == selection.keptId })
        assertEquals("b", selection.keptId)
    }
}
