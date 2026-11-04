package io.github.catlinux.criba.data.trash

import io.github.catlinux.criba.domain.ActionLog
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.DeletionCandidate
import io.github.catlinux.criba.domain.FakeActionLog
import io.github.catlinux.criba.domain.FakeTrashIndex
import io.github.catlinux.criba.domain.TrashFailure
import io.github.catlinux.criba.domain.TrashIndex
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTrashEngineTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var sources: File
    private lateinit var trash: File
    private lateinit var log: FakeActionLog
    private lateinit var index: FakeTrashIndex
    private var clock = 1_000L
    private var nextId = 0

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("root")
        sources = File(root, "Download").apply { mkdirs() }
        trash = File(root, "trash")
        log = FakeActionLog { 5L }
        index = FakeTrashIndex()
        clock = 1_000L
        nextId = 0
    }

    private fun engine(
        trashRoot: File? = trash,
        isProtected: (String) -> Boolean = { false },
        idGenerator: () -> String = { "id-${++nextId}" },
        actionLog: ActionLog = log,
        trashIndex: TrashIndex = index,
    ) = FileTrashEngine(
        trashRoot = trashRoot,
        log = actionLog,
        index = trashIndex,
        isProtected = isProtected,
        newId = idGenerator,
        nowEpochMillis = { clock },
    )

    private fun createFile(name: String, content: String = "contenido", directory: File = sources): File {
        val file = File(directory, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun candidate(file: File) =
        DeletionCandidate(file.absolutePath, file.name, file.length(), file.lastModified() / 1000)

    @Test
    fun `mueve un archivo a la papelera y lo registra`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val size = file.length()
        val events = mutableListOf<String>()
        val recordingLog = object : ActionLog {
            override suspend fun begin(
                type: ActionType,
                originalPath: String,
                trashId: String?,
                bytes: Long,
            ): Long {
                events += "begin"
                return log.begin(type, originalPath, trashId, bytes)
            }

            override suspend fun complete(id: Long, result: ActionResult, message: String?) {
                events += "complete:${result.name}"
                log.complete(id, result, message)
            }

            override suspend fun recent(limit: Int) = log.recent(limit)
        }

        val result = engine(actionLog = recordingLog).moveToTrash(listOf(candidate(file)))

        assertEquals(listOf("begin", "complete:SUCCESS"), events)
        assertFalse(file.exists())
        val destination = File(trash, "id-1/a.zip")
        assertTrue(destination.isFile)
        assertEquals("datos", destination.readText())
        assertEquals(size, result.movedBytes)
        val item = index.get("id-1")
        assertNotNull(item)
        assertEquals(TrashState.IN_TRASH, item?.state)
        assertEquals(file.absolutePath, item?.originalPath)
        assertEquals(destination.path, item?.trashPath)
        assertEquals(ActionResult.SUCCESS, log.recent().single().result)
    }

    @Test
    fun `no mueve si el tamano cambio desde el escaneo`() = runBlocking {
        val file = createFile("a.zip")

        val result = engine().moveToTrash(listOf(candidate(file).copy(sizeBytes = file.length() + 1)))

        assertEquals(TrashFailure.CHANGED_SINCE_SCAN, result.failed.single().failure)
        assertTrue(file.exists())
    }

    @Test
    fun `no mueve si la fecha cambio desde el escaneo`() = runBlocking {
        val file = createFile("a.zip")

        val result = engine().moveToTrash(listOf(candidate(file).copy(modifiedEpochSeconds = 1L)))

        assertEquals(TrashFailure.CHANGED_SINCE_SCAN, result.failed.single().failure)
        assertTrue(file.exists())
    }

    @Test
    fun `no mueve un archivo que ya no existe`() = runBlocking {
        val missing = File(sources, "no-existe.zip")

        val result = engine().moveToTrash(
            listOf(DeletionCandidate(missing.absolutePath, missing.name, 0, 0)),
        )

        assertEquals(TrashFailure.MISSING, result.failed.single().failure)
    }

    @Test
    fun `no mueve un directorio`() = runBlocking {
        val directory = File(sources, "carpeta").apply { mkdirs() }
        val candidate = DeletionCandidate(
            path = directory.absolutePath,
            displayName = directory.name,
            sizeBytes = 0,
            modifiedEpochSeconds = directory.lastModified() / 1000,
        )

        val result = engine().moveToTrash(listOf(candidate))

        assertEquals(TrashFailure.NOT_A_REGULAR_FILE, result.failed.single().failure)
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `no mueve un enlace simbolico`() = runBlocking {
        val target = createFile("objetivo.zip")
        val link = File(sources, "enlace.zip")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: Exception) {
            assumeTrue("el sistema no permite enlaces simbólicos", false)
        }

        val result = engine().moveToTrash(listOf(candidate(link)))

        assertEquals(TrashFailure.SYMLINK, result.failed.single().failure)
        assertTrue(target.exists())
    }

    @Test
    fun `no mueve una ruta con un enlace en un directorio intermedio`() = runBlocking {
        val real = File(root, "real").apply { mkdirs() }
        val linked = File(sources, "enlazada")
        try {
            Files.createSymbolicLink(linked.toPath(), real.toPath())
        } catch (e: Exception) {
            assumeTrue("el sistema no permite enlaces simbólicos", false)
        }
        val file = createFile("enlazada/dentro.zip", "x")

        val result = engine().moveToTrash(listOf(candidate(file)))

        assertEquals(TrashFailure.SYMLINK, result.failed.single().failure)
        assertTrue(file.exists())
    }

    @Test
    fun `no mueve una ruta que el predicado marca como protegida`() = runBlocking {
        val file = createFile("protegido.zip")

        val result = engine(isProtected = { it.endsWith("protegido.zip") }).moveToTrash(listOf(candidate(file)))

        assertEquals(TrashFailure.PROTECTED, result.failed.single().failure)
        assertTrue(file.exists())
    }

    @Test
    fun `dos archivos con el mismo nombre en carpetas distintas no colisionan`() = runBlocking {
        val first = createFile("a/igual.zip", "uno")
        val second = createFile("b/igual.zip", "dos")

        val result = engine().moveToTrash(listOf(candidate(first), candidate(second)))

        assertEquals(2, result.moved.size)
        assertEquals(setOf("id-1", "id-2"), result.moved.map { it.trashId }.toSet())
        assertEquals("uno", File(trash, "id-1/igual.zip").readText())
        assertEquals("dos", File(trash, "id-2/igual.zip").readText())
    }

    @Test
    fun `no sobrescribe si el destino ya existe`() = runBlocking {
        val first = createFile("a/igual.zip", "uno")
        val second = createFile("b/igual.zip", "dos")
        var id = "repetido"

        assertEquals(1, engine(idGenerator = { id }).moveToTrash(listOf(candidate(first))).moved.size)
        val result = engine(idGenerator = { id }).moveToTrash(listOf(candidate(second)))

        assertEquals(TrashFailure.TARGET_EXISTS, result.failed.single().failure)
        assertTrue(second.exists())
        assertEquals("uno", File(trash, "repetido/igual.zip").readText())
    }

    @Test
    fun `un fallo no impide mover los demas`() = runBlocking {
        val missing = File(sources, "no-existe.zip")
        val good = createFile("bueno.zip")

        val result = engine().moveToTrash(
            listOf(
                DeletionCandidate(missing.absolutePath, missing.name, 0, 0),
                candidate(good),
            ),
        )

        assertEquals(1, result.moved.size)
        assertEquals(1, result.failed.size)
        assertEquals(TrashFailure.MISSING, result.failed.single().failure)
        assertFalse(good.exists())
    }

    @Test
    fun `sin carpeta de papelera todo falla sin tocar nada`() = runBlocking {
        val file = createFile("a.zip")

        val result = engine(trashRoot = null).moveToTrash(listOf(candidate(file)))

        assertEquals(TrashFailure.TRASH_UNAVAILABLE, result.failed.single().failure)
        assertTrue(file.exists())
        assertEquals(0L, index.inTrashBytes())
    }

    @Test
    fun `si el indice falla el archivo vuelve a su sitio`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val failingIndex = object : TrashIndex {
            override suspend fun add(item: TrashItem) {
                throw IllegalStateException("boom")
            }

            override suspend fun setState(id: String, state: TrashState) = Unit

            override suspend fun get(id: String): TrashItem? = null

            override suspend fun inTrash(): List<TrashItem> = emptyList()

            override suspend fun inTrashBytes(): Long = 0L
        }

        val result = engine(trashIndex = failingIndex).moveToTrash(listOf(candidate(file)))

        assertEquals(TrashFailure.IO_ERROR, result.failed.single().failure)
        assertTrue(file.isFile)
        assertEquals("datos", file.readText())
        assertFalse(File(trash, "id-1").exists())
    }

    @Test
    fun `restaura un archivo a su ruta original`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId

        val result = engine().restore(id)

        assertEquals(file.absolutePath, result.restoredPath)
        assertFalse(result.renamed)
        assertNull(result.failure)
        assertEquals("datos", file.readText())
        assertFalse(File(trash, "$id/a.zip").exists())
        assertEquals(TrashState.RESTORED, index.get(id)?.state)
    }

    @Test
    fun `restaura con sufijo si el destino esta ocupado`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId
        createFile("a.zip", "nuevo")

        val result = engine().restore(id)

        assertEquals(File(sources, "a (restaurado).zip").absolutePath, result.restoredPath)
        assertTrue(result.renamed)
        assertEquals("nuevo", File(sources, "a.zip").readText())
        assertEquals("datos", File(sources, "a (restaurado).zip").readText())
    }

    @Test
    fun `restaura con sufijo dos si el primero tambien esta ocupado`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId
        createFile("a.zip", "nuevo")
        createFile("a (restaurado).zip", "tambien ocupado")

        val result = engine().restore(id)

        assertEquals(File(sources, "a (restaurado 2).zip").absolutePath, result.restoredPath)
        assertEquals("datos", File(sources, "a (restaurado 2).zip").readText())
    }

    @Test
    fun `recrea la carpeta original si ya no existe`() = runBlocking {
        val directory = File(sources, "sub").apply { mkdirs() }
        val file = createFile("a.zip", "datos", directory)
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId
        assertTrue(directory.delete())

        val result = engine().restore(id)

        assertEquals(file.absolutePath, result.restoredPath)
        assertEquals("datos", file.readText())
    }

    @Test
    fun `no restaura a una ruta protegida`() = runBlocking {
        var protect = false
        val file = createFile("a.zip")
        val id = engine(isProtected = { protect && it.endsWith("a.zip") })
            .moveToTrash(listOf(candidate(file)))
            .moved
            .single()
            .trashId
        protect = true

        val result = engine(isProtected = { protect && it.endsWith("a.zip") }).restore(id)

        assertEquals(TrashFailure.PROTECTED, result.failure)
        assertFalse(file.exists())
        assertTrue(File(trash, "$id/a.zip").exists())
        assertEquals(TrashState.IN_TRASH, index.get(id)?.state)
    }

    @Test
    fun `restaurar un id inexistente no toca nada`() = runBlocking {
        val result = engine().restore("no-existe")

        assertEquals(TrashFailure.NOT_IN_TRASH, result.failure)
        assertTrue(log.recent().isEmpty())
    }

    @Test
    fun `restaurar dos veces devuelve NOT_IN_TRASH la segunda`() = runBlocking {
        val file = createFile("a.zip")
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId
        engine().restore(id)

        val second = engine().restore(id)

        assertEquals(TrashFailure.NOT_IN_TRASH, second.failure)
        assertEquals(ActionResult.FAILED, log.recent().first().result)
    }

    @Test
    fun `vacia solo dentro de la papelera`() = runBlocking {
        val file = createFile("a.zip", "datos")
        val id = engine().moveToTrash(listOf(candidate(file))).moved.single().trashId
        val item = index.get(id)!!

        val result = engine().purgeItems(listOf(item))

        assertEquals(1, result.purged.size)
        assertEquals(item.bytes, result.purgedBytes)
        assertFalse(File(trash, "$id/a.zip").exists())
        assertFalse(File(trash, id).exists())
        assertEquals(TrashState.PURGED, index.get(id)?.state)
        assertEquals(ActionResult.SUCCESS, log.recent().first().result)
    }

    @Test
    fun `no borra un archivo fuera de la papelera`() = runBlocking {
        val outside = createFile("fuera.zip", "no tocar")
        val item = TrashItem(
            id = "t-1",
            originalPath = "/origen/fuera.zip",
            trashPath = outside.absolutePath,
            bytes = outside.length(),
            sha256 = null,
            trashedAtEpochMillis = 0,
            state = TrashState.IN_TRASH,
        )
        index.add(item)

        val result = engine().purgeItems(listOf(item))

        assertEquals(TrashFailure.OUTSIDE_TRASH, result.failed.single().failure)
        assertTrue(outside.exists())
        assertEquals(TrashState.IN_TRASH, index.get("t-1")?.state)
    }

    @Test
    fun `no acepta un prefijo de papelera enganoso`() = runBlocking {
        val fakeRoot = File(root, "trashX").apply { mkdirs() }
        val outside = createFile("a.zip", "no tocar", fakeRoot)
        val item = TrashItem(
            id = "t-1",
            originalPath = "/origen/a.zip",
            trashPath = outside.absolutePath,
            bytes = outside.length(),
            sha256 = null,
            trashedAtEpochMillis = 0,
            state = TrashState.IN_TRASH,
        )
        index.add(item)

        val result = engine().purgeItems(listOf(item))

        assertEquals(TrashFailure.OUTSIDE_TRASH, result.failed.single().failure)
        assertTrue(outside.exists())
    }

    @Test
    fun `si el archivo ya no estaba se considera purgado`() = runBlocking {
        val item = TrashItem(
            id = "t-1",
            originalPath = "/origen/a.zip",
            trashPath = File(trash, "t-1/a.zip").absolutePath,
            bytes = 0,
            sha256 = null,
            trashedAtEpochMillis = 0,
            state = TrashState.IN_TRASH,
        )
        index.add(item)

        val result = engine().purgeItems(listOf(item))

        assertEquals(1, result.purged.size)
        assertEquals(TrashState.PURGED, index.get("t-1")?.state)
        assertEquals("ya no estaba", log.recent().first().message)
    }

    @Test
    fun `purgeExpired respeta el umbral exacto`() = runBlocking {
        val now = 10_000_000_000L
        val threshold = now - 30 * 86_400_000L
        val expired = putInTrash("vieja", threshold)
        val recent = putInTrash("reciente", threshold + 1)
        val viva = putInTrash("viva", threshold + 1_000)

        val result = engine().purgeExpired(now, retentionDays = 30)

        assertEquals(listOf(expired.id), result.purged.map { it.trashId })
        assertEquals(TrashState.PURGED, index.get(expired.id)?.state)
        assertEquals(TrashState.IN_TRASH, index.get(recent.id)?.state)
        assertEquals(TrashState.IN_TRASH, index.get(viva.id)?.state)
        assertTrue(File(recent.trashPath).exists())
        assertTrue(File(viva.trashPath).exists())
    }

    private suspend fun putInTrash(id: String, trashedAtEpochMillis: Long): TrashItem {
        val directory = File(trash, id).apply { mkdirs() }
        val file = File(directory, "a.zip").apply { writeText("datos") }
        val item = TrashItem(
            id = id,
            originalPath = "/origen/$id/a.zip",
            trashPath = file.absolutePath,
            bytes = file.length(),
            sha256 = null,
            trashedAtEpochMillis = trashedAtEpochMillis,
            state = TrashState.IN_TRASH,
        )
        index.add(item)
        return item
    }
}
