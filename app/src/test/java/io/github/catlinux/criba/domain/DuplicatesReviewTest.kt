package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesReviewTest {
    private val storageRoot = "/storage/emulated/0"

    private fun file(
        name: String,
        relativePath: String = "Download/",
        size: Long = 100,
        modified: Long = 100,
        id: String = name,
    ) = ScanFile(id, name, size, modified, relativePath)

    private fun group(vararg files: ScanFile, hash: String = "hash") =
        DuplicateGroup(hash, files.toList(), files.first().sizeBytes)

    @Test
    fun `por defecto se marcan todas las copias elegibles`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip"), file("b.zip"))),
            storageRoot,
        )

        assertEquals(1, review.markedCount)
        assertNotNull(review.confirm())
    }

    @Test
    fun `sin marcadas el confirmado es null`() {
        val review = DuplicatesReview(listOf(group(file("a.zip"), file("b.zip"))), storageRoot).clear()

        assertEquals(0, review.markedCount)
        assertNull(review.confirm())
    }

    @Test
    fun `toggle desmarca y vuelve a marcar una copia seleccionable`() {
        val review = DuplicatesReview(listOf(group(file("a.zip"), file("b.zip"))), storageRoot)
        val kept = review.selectionOf("hash")!!.keptId
        val other = review.selectionOf("hash")!!.toDelete.single()

        val unmarked = review.toggle(other.id)
        val markedAgain = unmarked.toggle(other.id)

        assertFalse(unmarked.isMarked(other.id))
        assertEquals(0, unmarked.markedCount)
        assertTrue(markedAgain.isMarked(other.id))
        assertEquals(1, markedAgain.markedCount)
        assertFalse(markedAgain.isMarked(kept))
    }

    @Test
    fun `toggle de la copia conservada no hace nada`() {
        val review = DuplicatesReview(listOf(group(file("a.zip"), file("b.zip"))), storageRoot)
        val kept = review.selectionOf("hash")!!.keptId

        assertTrue(review.toggle(kept) === review)
    }

    @Test
    fun `cambiar la copia conservada deja la anterior como candidata`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip"), file("b.zip"), file("c.zip"))),
            storageRoot,
        )
        val previousKept = review.selectionOf("hash")!!.keptId
        val newKept = review.selectionOf("hash")!!.toDelete.first().id

        val changed = review.withKept("hash", newKept)

        assertEquals(newKept, changed.selectionOf("hash")!!.keptId)
        assertTrue(changed.selectionOf("hash")!!.toDelete.any { it.id == previousKept })
        assertFalse(changed.isMarked(newKept))
    }

    @Test
    fun `no se puede conservar una copia ajena al grupo`() {
        val review = DuplicatesReview(listOf(group(file("a.zip"), file("b.zip"))), storageRoot)

        assertThrows(IllegalArgumentException::class.java) { review.withKept("hash", "otro") }
        assertTrue(review.withKept("desconocido", "a.zip") === review)
    }

    @Test
    fun `la multimedia no se puede marcar y se cuenta como bloqueada o sin copia`() {
        val review = DuplicatesReview(
            listOf(group(file("a.jpg", "Pictures/"), file("b.jpg", "Pictures/"))),
            storageRoot,
        )
        val kept = review.selectionOf("hash")!!.keptId
        val other = review.selectionOf("hash")!!.toDelete.single()

        val attempted = review.toggle(other.id)
        val changedKept = review.withKept("hash", other.id)

        assertEquals(0, attempted.markedCount)
        assertEquals(0, changedKept.markedCount)
        assertFalse(review.isMarked(kept))
        assertEquals(1, review.blockedOrBackupCount)
        assertEquals(ReviewStatus.NEEDS_BACKUP, review.statusOf(other.id))
    }

    @Test
    fun `una copia protegida queda bloqueada y no se marca`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip", "Android/data/x/"), file("b.zip", "Android/data/x/"))),
            storageRoot,
        )
        val other = review.selectionOf("hash")!!.toDelete.single()

        assertEquals(ReviewStatus.BLOCKED, review.statusOf(other.id))
        assertEquals(0, review.toggle(other.id).markedCount)
    }

    @Test
    fun `seleccionar todo marca solo las copias elegibles`() {
        val review = DuplicatesReview(
            listOf(
                group(file("a.zip"), file("b.zip"), file("c.zip"), hash = "docs"),
                group(file("a.jpg", "Pictures/"), file("b.jpg", "Pictures/"), hash = "fotos"),
            ),
            storageRoot,
        )

        val all = review.selectAll()

        assertEquals(2, all.markedCount)
        assertEquals(2, all.markedFiles.size)
        assertTrue(all.clear().markedCount == 0)
    }

    @Test
    fun `los bytes marcados son la suma real y el confirmado usa ese total`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip", size = 300), file("b.zip", size = 300), file("c.zip", size = 300))),
            storageRoot,
        ).selectAll()

        assertEquals(600L, review.markedBytes)
        val confirmed = review.confirm()
        assertNotNull(confirmed)
        assertEquals(600L, confirmed?.totalBytes)
        assertEquals(2, confirmed?.items?.size)
    }

    @Test
    fun `el confirmado nunca contiene la copia conservada`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip"), file("b.zip"), file("c.zip"))),
            storageRoot,
        ).selectAll()
        val kept = review.selectionOf("hash")!!.keptFile

        assertFalse(review.confirm()!!.items.any { it.displayName == kept.displayName })
    }

    @Test
    fun `dos grupos no comparten archivos marcados`() {
        val review = DuplicatesReview(
            listOf(
                group(file("a.zip"), file("b.zip"), hash = "uno"),
                group(file("c.pdf"), file("d.pdf"), hash = "dos"),
            ),
            storageRoot,
        ).selectAll()

        assertEquals(2, review.markedCount)
        assertEquals(review.markedFiles.map { it.id }.toSet().size, review.markedFiles.size)
    }

    @Test
    fun `al quitar archivos movidos los grupos que quedan con una copia desaparecen`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip"), file("b.zip"), hash = "dos")),
            storageRoot,
        ).selectAll()
        val moved = review.markedFiles.map { it.id }.toSet()

        val remaining = review.removeFiles(moved)

        assertTrue(remaining.groups.isEmpty())
        assertTrue(remaining.markedFiles.isEmpty())
    }

    @Test
    fun `al quitar una copia de un grupo de tres sigue habiendo grupo de dos`() {
        val review = DuplicatesReview(
            listOf(group(file("a.zip"), file("b.zip"), file("c.zip"))),
            storageRoot,
        ).selectAll()
        val moved = review.selectionOf("hash")!!.toDelete.first().id

        val remaining = review.removeFiles(setOf(moved))

        assertEquals(1, remaining.groups.size)
        assertEquals(2, remaining.groups.single().files.size)
        assertEquals(1, remaining.markedCount)
        assertFalse(remaining.markedFiles.any { it.id == moved })
    }
}
