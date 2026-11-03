package io.github.catlinux.criba.data

import io.github.catlinux.criba.domain.ScanFile
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadsWalkerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var storage: File
    private lateinit var root: File

    @Before
    fun setUp() {
        storage = temporaryFolder.newFolder("storage")
        root = File(storage, "Download").apply { mkdirs() }
    }

    private fun createFile(relativePath: String, sizeBytes: Long, modifiedSeconds: Long = 1_700_000_000L): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
        file.setLastModified(modifiedSeconds * 1000)
        return file
    }

    private fun walk(isProtected: (String) -> Boolean = { false }): List<ScanFile> =
        walkDownloads(root, storage, isProtected)

    @Test
    fun `devuelve ids canonicos nombres tamanos y ruta relativa con barra final`() {
        val nota = createFile("nota.txt", 10)
        val zip = createFile("sub/descarga.zip", 2048)

        val files = walk().associateBy { it.id }

        assertEquals(setOf(nota.canonicalPath, zip.canonicalPath), files.keys)
        val notaFile = files.getValue(nota.canonicalPath)
        assertEquals("nota.txt", notaFile.displayName)
        assertEquals(10L, notaFile.sizeBytes)
        assertEquals(1_700_000_000L, notaFile.modifiedEpochSeconds)
        assertEquals("Download/", notaFile.relativePath)
        val zipFile = files.getValue(zip.canonicalPath)
        assertEquals("descarga.zip", zipFile.displayName)
        assertEquals(2048L, zipFile.sizeBytes)
        assertEquals("Download/sub/", zipFile.relativePath)
    }

    @Test
    fun `devuelve lista vacia si la raiz no existe`() {
        assertEquals(emptyList<ScanFile>(), walkDownloads(File(storage, "no-existe"), storage) { false })
    }

    @Test
    fun `omite los archivos protegidos`() {
        val normal = createFile("normal.txt", 1)
        val secreto = createFile("secreto.txt", 2)

        val files = walk { it == secreto.canonicalPath }

        assertEquals(listOf(normal.canonicalPath), files.map { it.id })
    }

    @Test
    fun `omite una carpeta protegida sin descender en ella`() {
        val abierto = createFile("abierto.txt", 1)
        createFile("privada/dentro.txt", 2)
        val privada = File(root, "privada").canonicalPath

        val files = walk { it == privada }

        assertEquals(listOf(abierto.canonicalPath), files.map { it.id })
    }

    @Test
    fun `no devuelve directorios`() {
        createFile("sub/dentro.txt", 1)

        val files = walk()

        assertEquals(listOf("dentro.txt"), files.map { it.displayName })
        assertTrue(files.none { it.displayName == "sub" })
    }
}
