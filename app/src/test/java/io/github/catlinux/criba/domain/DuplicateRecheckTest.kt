package io.github.catlinux.criba.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateRecheckTest {
    private fun file(name: String, id: String = name) =
        ScanFile(id, name, 10, 0, "Download/")

    private fun group(vararg files: ScanFile) = DuplicateGroup("hash-bueno", files.toList(), 10)

    private fun recheck(hashes: Map<String, String?>) =
        DuplicateRecheck(ContentHasher { hashes[it.id] })

    @Test
    fun `si todo coincide devuelve todas las copias a borrar`() = runSuspend {
        val a = file("a")
        val b = file("b")
        val c = file("c")
        val selection = DuplicateSelection(group(a, b, c), keptId = a.id)

        val confirmed = recheck(mapOf("a" to "hash-bueno", "b" to "hash-bueno", "c" to "hash-bueno"))
            .confirmedDeletions(selection)

        assertEquals(listOf("b", "c"), confirmed.map { it.id })
    }

    @Test
    fun `si la conservada es ilegible no se confirma nada`() = runSuspend {
        val a = file("a")
        val selection = DuplicateSelection(group(a, file("b")), keptId = a.id)

        val confirmed = recheck(mapOf("a" to null, "b" to "hash-bueno")).confirmedDeletions(selection)

        assertEquals(emptyList<ScanFile>(), confirmed)
    }

    @Test
    fun `si la conservada cambio de hash no se confirma nada`() = runSuspend {
        val a = file("a")
        val selection = DuplicateSelection(group(a, file("b")), keptId = a.id)

        val confirmed = recheck(mapOf("a" to "otro-hash", "b" to "hash-bueno")).confirmedDeletions(selection)

        assertEquals(emptyList<ScanFile>(), confirmed)
    }

    @Test
    fun `una copia cambiada se excluye solo a ella`() = runSuspend {
        val a = file("a")
        val b = file("b")
        val c = file("c")
        val selection = DuplicateSelection(group(a, b, c), keptId = a.id)

        val confirmed = recheck(
            mapOf("a" to "hash-bueno", "b" to "otro-hash", "c" to "hash-bueno"),
        ).confirmedDeletions(selection)

        assertEquals(listOf("c"), confirmed.map { it.id })
    }

    @Test
    fun `una copia ilegible se excluye`() = runSuspend {
        val a = file("a")
        val b = file("b")
        val selection = DuplicateSelection(group(a, b), keptId = a.id)

        val confirmed = recheck(mapOf("a" to "hash-bueno", "b" to null)).confirmedDeletions(selection)

        assertEquals(emptyList<ScanFile>(), confirmed)
    }

    @Test
    fun `nunca devuelve la copia conservada`() = runSuspend {
        val a = file("a")
        val b = file("b")
        val selection = DuplicateSelection(group(a, b), keptId = a.id)

        val confirmed = recheck(mapOf("a" to "hash-bueno", "b" to "hash-bueno")).confirmedDeletions(selection)

        assertTrue(confirmed.none { it.id == selection.keptId })
    }

    @Test
    fun `la cancelacion propaga CancellationException`() = runBlocking {
        val files = (1..5).map { file("f$it") }
        val selection = DuplicateSelection(group(*files.toTypedArray()), keptId = "f1")
        var hashed = 0
        val checker = DuplicateRecheck(
            ContentHasher {
                hashed++
                if (hashed == 3) currentCoroutineContext().cancel()
                "hash-bueno"
            },
        )

        val job = launch(Dispatchers.Default) { checker.confirmedDeletions(selection) }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(hashed <= 3)
    }
}
