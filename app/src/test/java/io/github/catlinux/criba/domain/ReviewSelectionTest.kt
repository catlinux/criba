package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSelectionTest {
    private fun file(
        name: String,
        size: Long = 100,
        relativePath: String? = "Download/",
        id: String = name,
    ) = ScanFile(id, name, size, 1_700_000_000, relativePath)

    @Test
    fun `construye la ruta exacta con y sin barra final en storageRoot`() {
        assertEquals(
            "/storage/emulated/0/Download/a.zip",
            file("a.zip").toDeletionCandidate("/storage/emulated/0/")?.path,
        )
        assertEquals(
            "/storage/emulated/0/Download/a.zip",
            file("a.zip").toDeletionCandidate("/storage/emulated/0")?.path,
        )
    }

    @Test
    fun `rechaza ruta relativa nula y ScanFile impide tamanos negativos`() {
        assertNull(file("a.zip", relativePath = null).toDeletionCandidate("/storage/emulated/0"))
        assertThrows(IllegalArgumentException::class.java) { file("a.zip", size = -1) }
    }

    @Test
    fun `el candidato conserva tamano y fecha`() {
        val candidate = file("a.zip", size = 123).toDeletionCandidate("/storage/emulated/0")!!

        assertEquals(123L, candidate.sizeBytes)
        assertEquals(1_700_000_000L, candidate.modifiedEpochSeconds)
    }

    @Test
    fun `clasifica archivos normales multimedia y protegidos manteniendo el orden`() {
        val files = listOf(
            file("a.zip"),
            file("a.apk"),
            file("a.jpg"),
            file("a.MP4"),
            file("a.mp3"),
            file("a.JPG"),
            file("x.zip", relativePath = null),
            file("x.zip", relativePath = "Android/data/otra.app/"),
            file("x.db"),
        )

        val items = ReviewBuilder.build(files, "/storage/emulated/0")

        assertEquals(files.map { it.id }, items.map { it.file.id })
        assertEquals(ReviewStatus.SELECTABLE, items[0].status)
        assertEquals(ReviewStatus.SELECTABLE, items[1].status)
        assertEquals(ReviewStatus.NEEDS_BACKUP, items[2].status)
        assertEquals(ReviewStatus.NEEDS_BACKUP, items[3].status)
        assertEquals(ReviewStatus.NEEDS_BACKUP, items[4].status)
        assertEquals(ReviewStatus.NEEDS_BACKUP, items[5].status)
        assertEquals(ReviewStatus.BLOCKED, items[6].status)
        assertEquals(ReviewStatus.BLOCKED, items[7].status)
        assertEquals(ReviewStatus.BLOCKED, items[8].status)
    }

    @Test
    fun `toggle selecciona y deselecciona`() {
        val initial = ReviewSelection(ReviewBuilder.build(listOf(file("a.zip")), "/storage/emulated/0"))

        val selected = initial.toggle("a.zip")
        assertEquals(setOf("a.zip"), selected.selectedIds)
        assertEquals(emptySet<String>(), selected.toggle("a.zip").selectedIds)
    }

    @Test
    fun `toggle ignora multimedia protegidos y ids desconocidos`() {
        val initial = ReviewSelection(
            ReviewBuilder.build(
                listOf(file("a.jpg"), file("b.zip", relativePath = "Android/data/x/")),
                "/storage/emulated/0",
            ),
        )

        assertTrue(initial.toggle("a.jpg") === initial)
        assertTrue(initial.toggle("b.zip") === initial)
        assertTrue(initial.toggle("missing") === initial)
        assertTrue(initial.selectedIds.isEmpty())
    }

    @Test
    fun `selectAllSelectable no marca multimedia ni protegidos`() {
        val selection = ReviewSelection(
            ReviewBuilder.build(
                listOf(file("a.zip"), file("b.jpg"), file("c.zip", relativePath = "Android/data/x/")),
                "/storage/emulated/0",
            ),
        ).selectAllSelectable()

        assertEquals(setOf("a.zip"), selection.selectedIds)
    }

    @Test
    fun `clear quita toda la seleccion`() {
        val selection = ReviewSelection(
            ReviewBuilder.build(listOf(file("a.zip")), "/storage/emulated/0"),
        ).selectAllSelectable()

        assertTrue(selection.clear().selectedIds.isEmpty())
    }

    @Test
    fun `bytes y recuento seleccionados son reales y conservan orden`() {
        val selection = ReviewSelection(
            ReviewBuilder.build(listOf(file("a.zip", 300), file("b.apk", 200), file("c.jpg", 900)), "/storage/emulated/0"),
        ).selectAllSelectable()

        assertEquals(500L, selection.selectedBytes)
        assertEquals(2, selection.selectedCount)
        assertEquals(listOf("a.zip", "b.apk"), selection.selectedCandidates.map { it.displayName })
    }

    @Test
    fun `confirm devuelve null sin seleccion`() {
        val selection = ReviewSelection(ReviewBuilder.build(listOf(file("a.zip")), "/storage/emulated/0"))

        assertNull(selection.confirm())
    }

    @Test
    fun `confirm solo devuelve los candidatos seleccionados y sus bytes reales`() {
        val selection = ReviewSelection(
            ReviewBuilder.build(listOf(file("a.zip", 300), file("b.apk", 200)), "/storage/emulated/0"),
        ).toggle("a.zip").toggle("b.apk")

        val confirmed = selection.confirm()!!

        assertEquals(500L, confirmed.totalBytes)
        assertEquals(listOf("a.zip", "b.apk"), confirmed.items.map { it.displayName })
    }

    @Test
    fun `el estado de seleccion es inmutable`() {
        val items = ReviewBuilder.build(listOf(file("a.zip")), "/storage/emulated/0")
        val sourceIds = mutableSetOf("a.zip")
        val selection = ReviewSelection(items, sourceIds)
        sourceIds.clear()

        assertTrue(selection.selectedIds.contains("a.zip"))
        assertFalse(selection.clear().selectedIds.contains("a.zip"))
    }

    @Test
    fun `el constructor ignora ids no seleccionables`() {
        val items = ReviewBuilder.build(
            listOf(file("normal.zip"), file("foto.jpg")),
            "/storage/emulated/0",
        )

        val selection = ReviewSelection(items, setOf("normal.zip", "foto.jpg", "desconocido"))

        assertEquals(setOf("normal.zip"), selection.selectedIds)
        assertEquals(1, selection.selectedCount)
    }
}
