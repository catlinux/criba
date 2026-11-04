package io.github.catlinux.criba.data.duplicates

import io.github.catlinux.criba.domain.ScanFile
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileContentHasherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var hasher: FileContentHasher

    @Before
    fun setUp() {
        root = temporaryFolder.newFolder("storage")
        hasher = FileContentHasher(root.path)
    }

    private fun file(name: String, content: ByteArray): File =
        File(root, name).apply { writeBytes(content) }

    private fun hashOf(path: File, size: Long = path.length()): String? = runBlocking {
        hasher.hashPath(path.path, size)
    }

    @Test
    fun `calcula el sha256 de un contenido conocido`() {
        val file = file("abc.txt", "abc".toByteArray())

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashOf(file))
    }

    @Test
    fun `un tamano distinto devuelve null`() {
        val file = file("a.txt", "abc".toByteArray())

        assertNull(hashOf(file, size = file.length() + 1))
    }

    @Test
    fun `un archivo inexistente devuelve null`() {
        assertNull(hashOf(File(root, "no-existe.txt"), size = 10))
    }

    @Test
    fun `un directorio devuelve null`() {
        val directory = File(root, "carpeta").apply { mkdirs() }

        assertNull(hashOf(directory, size = 0))
    }

    @Test
    fun `un enlace simbolico devuelve null`() {
        val target = file("objetivo.txt", "abc".toByteArray())
        val link = File(root, "enlace.txt")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (e: Exception) {
            assumeTrue("el sistema no permite enlaces simbólicos", false)
        }

        assertNull(hashOf(link, size = target.length()))
    }

    @Test
    fun `hashea un archivo de varios buffers`() {
        val content = ByteArray(200_000) { (it % 251).toByte() }
        val file = file("grande.bin", content)
        val expected = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }

        assertEquals(expected, hashOf(file))
    }

    @Test
    fun `no lee una ruta protegida`() {
        val scanFile = ScanFile(
            id = "protegido",
            displayName = "x.zip",
            sizeBytes = 100,
            modifiedEpochSeconds = 0,
            relativePath = "Android/data/otra.app/",
        )
        val hasher = FileContentHasher("/storage/emulated/0")

        assertNull(runBlocking { hasher.sha256(scanFile) })
    }

    @Test
    fun `no hashea un archivo sin ruta relativa`() {
        val scanFile = ScanFile("sin-ruta", "x.zip", 100, 0, null)

        assertNull(runBlocking { hasher.sha256(scanFile) })
    }
}
