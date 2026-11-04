package io.github.catlinux.criba.data.duplicates

import android.os.Build
import android.os.Environment
import io.github.catlinux.criba.domain.DuplicateRecheck
import io.github.catlinux.criba.domain.DuplicateSelection
import io.github.catlinux.criba.domain.DuplicatesScanner
import io.github.catlinux.criba.domain.FileSource
import io.github.catlinux.criba.domain.ScanFile
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class DuplicatesDeviceTest {
    private lateinit var directory: File
    private lateinit var storageRoot: String
    private lateinit var hasher: FileContentHasher

    @Before
    fun setUp() {
        assumeTrue(
            "requiere acceso a todos los archivos",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager(),
        )
        directory = File(Environment.getExternalStorageDirectory(), DIRECTORY_NAME)
        if (directory.exists()) deleteTree(directory)
        assertTrue(directory.mkdirs())
        storageRoot = Environment.getExternalStorageDirectory().path
        hasher = FileContentHasher(storageRoot)
    }

    @After
    fun tearDown() {
        if (::directory.isInitialized) deleteTree(directory)
    }

    private fun deleteTree(file: File) {
        file.listFiles()?.forEach { deleteTree(it) }
        file.delete()
    }

    private fun write(name: String, content: ByteArray): File =
        File(directory, name).apply { writeBytes(content) }

    private fun scanFile(file: File) =
        ScanFile(
            id = file.absolutePath,
            displayName = file.name,
            sizeBytes = file.length(),
            modifiedEpochSeconds = file.lastModified() / 1000,
            relativePath = "$DIRECTORY_NAME/",
        )

    @Test
    fun detectaDuplicadosRealesYConfirmaLaCopiaSobrante() = runBlocking {
        val content = ByteArray(200_000) { (it % 251).toByte() }
        val first = write("uno.bin", content)
        val second = write("dos.bin", content)
        val different = write("tres.bin", content.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() })

        val files = listOf(first, second, different).map(::scanFile)
        val scanner = DuplicatesScanner(FileSource { files }, hasher)

        val groups = scanner.scan()

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().files.size)
        assertEquals(first.length(), groups.single().reclaimableBytes)

        val selection = DuplicateSelection(groups.single())
        val confirmed = DuplicateRecheck(hasher).confirmedDeletions(selection)

        assertEquals(1, confirmed.size)
        assertEquals(selection.toDelete.map { it.id }, confirmed.map { it.id })
    }

    private companion object {
        const val DIRECTORY_NAME = "criba-test-dup"
    }
}
