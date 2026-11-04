package io.github.catlinux.criba.data.trash

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.catlinux.criba.data.db.CribaDatabase
import io.github.catlinux.criba.data.db.RoomActionLog
import io.github.catlinux.criba.data.db.RoomTrashIndex
import io.github.catlinux.criba.domain.ConfirmedDeletion
import io.github.catlinux.criba.domain.ConfirmedPurge
import io.github.catlinux.criba.domain.DeletionPolicy
import io.github.catlinux.criba.domain.DeletionCandidate
import io.github.catlinux.criba.domain.ProtectedPaths
import io.github.catlinux.criba.domain.TrashState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashDeviceTest {
    private lateinit var context: Context
    private lateinit var database: CribaDatabase
    private lateinit var actionLog: RoomActionLog
    private lateinit var trashIndex: RoomTrashIndex
    private lateinit var testDirectory: File
    private lateinit var trashRoot: File
    private lateinit var engine: FileTrashEngine

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "requiere acceso a todos los archivos",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager(),
        )
        testDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "criba-test-${System.nanoTime()}",
        )
        assertTrue(testDirectory.mkdirs())

        database = Room.inMemoryDatabaseBuilder(context, CribaDatabase::class.java).build()
        actionLog = RoomActionLog(database.actionLogDao())
        trashIndex = RoomTrashIndex(database.trashDao())
        @Suppress("DEPRECATION")
        trashRoot = File(Environment.getExternalStorageDirectory(), DeletionPolicy.TRASH_DIRECTORY_NAME)
        engine = FileTrashEngine(
            trashRoot = trashRoot,
            log = actionLog,
            index = trashIndex,
            isProtected = ProtectedPaths::isProtected,
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::testDirectory.isInitialized) deleteTree(testDirectory)
    }

    private fun candidate(file: File) =
        DeletionCandidate(file.absolutePath, file.name, file.length(), file.lastModified() / 1000)

    private fun deleteTree(file: File) {
        file.listFiles()?.forEach { deleteTree(it) }
        file.delete()
    }

    @Test
    fun cicloCompletoMoverRestaurarYMoverYVaciar() = runBlocking {
        val text = File(testDirectory, "nota.txt").apply { writeText("hola") }
        val zip = File(testDirectory, "falso.zip").apply { writeText("datos") }
        val jpg = File(testDirectory, "foto.jpg").apply { writeText("imagen") }
        val totalBytes = text.length() + zip.length()

        val candidates = listOf(candidate(text), candidate(zip))
        val confirmed = ConfirmedDeletion.confirm(candidates, candidates.sumOf { it.sizeBytes })
        assertNotNull(confirmed)
        assertNull(
            "un jpg debe rechazarse hasta la copia verificada",
            ConfirmedDeletion.confirm(listOf(candidate(jpg)), jpg.length()),
        )

        val moved = engine.moveToTrash(confirmed!!.items)
        assertEquals(moved.failed.toString(), 2, moved.moved.size)
        assertEquals(totalBytes, moved.movedBytes)
        assertFalse(text.exists())
        assertFalse(zip.exists())
        assertEquals(totalBytes, trashIndex.inTrashBytes())
        moved.moved.forEach { trashed ->
            assertTrue(File(trashRoot, "${trashed.trashId}/${File(trashed.originalPath).name}").exists())
        }

        moved.moved.forEach { trashed ->
            val restored = engine.restore(trashed.trashId)
            assertNull(restored.failure)
            assertEquals(trashed.originalPath, restored.restoredPath)
            assertTrue(File(trashed.originalPath).isFile)
        }
        assertEquals(0L, trashIndex.inTrashBytes())

        val again = listOf(candidate(text), candidate(zip))
        val confirmedAgain = ConfirmedDeletion.confirm(again, again.sumOf { it.sizeBytes })
        assertNotNull(confirmedAgain)
        val movedAgain = engine.moveToTrash(confirmedAgain!!.items)
        assertEquals(2, movedAgain.moved.size)
        assertEquals(totalBytes, trashIndex.inTrashBytes())

        val inTrash = trashIndex.inTrash()
        assertEquals(2, inTrash.size)
        assertTrue(inTrash.all { it.state == TrashState.IN_TRASH })
        val purge = ConfirmedPurge.confirm(inTrash, inTrash.sumOf { it.bytes })
        assertNotNull(purge)

        val purged = engine.purgeItems(purge!!.items)
        assertEquals(2, purged.purged.size)
        assertEquals(totalBytes, purged.purgedBytes)
        assertEquals(0L, trashIndex.inTrashBytes())
        inTrash.forEach { assertFalse(File(it.trashPath).exists()) }
    }
}
