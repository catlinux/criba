package io.github.catlinux.criba.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatesScannerTest {
    private fun file(
        name: String,
        size: Long,
        relativePath: String? = "Download/",
        id: String = name,
        modified: Long = 0,
    ) = ScanFile(id, name, size, modified, relativePath)

    private fun scan(
        files: List<ScanFile>,
        hashes: Map<String, String?>,
        onProgress: (DuplicateProgress) -> Unit = {},
    ): List<DuplicateGroup> = runSuspend {
        DuplicatesScanner(
            source = FileSource { files },
            hasher = ContentHasher { hashes[it.id] },
        ).scan(onProgress)
    }

    @Test
    fun `una fuente vacia no devuelve grupos`() {
        assertEquals(emptyList<DuplicateGroup>(), scan(emptyList(), emptyMap()))
    }

    @Test
    fun `ignora los archivos de tamano cero`() {
        val files = listOf(file("a", 0), file("b", 0))

        assertEquals(emptyList<DuplicateGroup>(), scan(files, mapOf("a" to "x", "b" to "x")))
    }

    @Test
    fun `el mismo tamano con contenido distinto no agrupa`() {
        val files = listOf(file("a", 10), file("b", 10))

        assertEquals(emptyList<DuplicateGroup>(), scan(files, mapOf("a" to "uno", "b" to "dos")))
    }

    @Test
    fun `dos archivos identicos forman un grupo`() {
        val files = listOf(file("a", 10), file("b", 10))
        val groups = scan(files, mapOf("a" to "mismo", "b" to "mismo"))

        assertEquals(1, groups.size)
        assertEquals(10L, groups.single().reclaimableBytes)
        assertEquals(listOf("a", "b"), groups.single().files.map { it.id })
    }

    @Test
    fun `tres copias dejan dos tamanos recuperables`() {
        val files = listOf(file("a", 10), file("b", 10), file("c", 10))
        val groups = scan(files, mapOf("a" to "mismo", "b" to "mismo", "c" to "mismo"))

        assertEquals(3, groups.single().files.size)
        assertEquals(20L, groups.single().reclaimableBytes)
    }

    @Test
    fun `un archivo ilegible se ignora y el grupo desaparece si queda uno`() {
        val files = listOf(file("a", 10), file("b", 10), file("c", 10))
        val groups = scan(files, mapOf("a" to "mismo", "b" to null, "c" to "mismo"))

        assertEquals(listOf("a", "c"), groups.single().files.map { it.id })
    }

    @Test
    fun `el mismo archivo con ids distintos no cuenta como duplicado`() {
        val files = listOf(
            file("a", 10, id = "media:1"),
            file("a", 10, id = "/storage/a"),
        )

        assertEquals(emptyList<DuplicateGroup>(), scan(files, mapOf("media:1" to "x", "/storage/a" to "x")))
    }

    @Test
    fun `solo hashea archivos de grupos con al menos dos del mismo tamano`() {
        val files = listOf(file("a", 10), file("b", 10), file("solo", 20))
        var calls = 0
        val groups = runSuspend {
            DuplicatesScanner(
                source = FileSource { files },
                hasher = ContentHasher {
                    calls++
                    "mismo"
                },
            ).scan()
        }

        assertEquals(2, calls)
        assertEquals(1, groups.size)
    }

    @Test
    fun `ordena por recuperable descendente y desempata por hash`() {
        val files = listOf(
            file("a1", 100), file("a2", 100), file("a3", 100),
            file("b1", 1_000), file("b2", 1_000),
            file("c1", 100), file("c2", 100),
        )
        val hashes = mapOf(
            "a1" to "hash-a", "a2" to "hash-a", "a3" to "hash-a",
            "b1" to "hash-c", "b2" to "hash-c",
            "c1" to "hash-b", "c2" to "hash-b",
        )

        val groups = scan(files, hashes)

        assertEquals(listOf(1_000L, 200L, 100L), groups.map { it.reclaimableBytes })
        assertEquals(listOf("hash-c", "hash-a", "hash-b"), groups.map { it.sha256 })
    }

    @Test
    fun `ordena los archivos del grupo por ruta y nombre`() {
        val files = listOf(
            file("zeta", 10, relativePath = "DCIM/", id = "zeta-dcim"),
            file("alfa", 10, relativePath = "Download/", id = "alfa-download"),
            file("alfa", 10, relativePath = "DCIM/", id = "alfa-dcim"),
        )
        val hashes = mapOf("zeta-dcim" to "h", "alfa-download" to "h", "alfa-dcim" to "h")

        val groups = scan(files, hashes)

        assertEquals(
            listOf("DCIM/alfa", "DCIM/zeta", "Download/alfa"),
            groups.first().files.map { "${it.relativePath}${it.displayName}" },
        )
    }

    @Test
    fun `informa del progreso con el total de archivos a hashear`() {
        val files = listOf(file("a", 10), file("b", 10), file("c", 20), file("d", 20), file("solo", 30))
        val progress = mutableListOf<DuplicateProgress>()

        scan(files, mapOf("a" to "x", "b" to "x", "c" to "y", "d" to "y"), progress::add)

        assertEquals(4, progress.size)
        assertEquals(listOf(1, 2, 3, 4), progress.map { it.filesDone })
        assertTrue(progress.all { it.filesTotal == 4 })
    }

    @Test
    fun `la cancelacion detiene el escaneo y propaga CancellationException`() = runBlocking {
        val files = (1..10).map { file("f$it", 10) }
        var hashed = 0
        val scanner = DuplicatesScanner(
            source = FileSource { files },
            hasher = ContentHasher {
                hashed++
                if (hashed == 3) currentCoroutineContext().cancel()
                "mismo"
            },
        )

        val job = launch(Dispatchers.Default) { scanner.scan() }
        job.join()

        assertTrue(job.isCancelled)
        assertTrue("el escaneo no debe seguir hasheando tras cancelar", hashed <= 3)
    }
}
