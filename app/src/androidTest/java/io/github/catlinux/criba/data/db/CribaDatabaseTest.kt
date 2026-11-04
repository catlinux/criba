package io.github.catlinux.criba.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CribaDatabaseTest {
    private lateinit var database: CribaDatabase
    private lateinit var actionLog: RoomActionLog
    private lateinit var trashIndex: RoomTrashIndex

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun item(id: String, bytes: Long = 100, trashedAt: Long = 0) = TrashItem(
        id = id,
        originalPath = "/storage/emulated/0/Download/$id",
        trashPath = "/storage/emulated/0/.criba-trash/$id",
        bytes = bytes,
        sha256 = null,
        trashedAtEpochMillis = trashedAt,
        state = TrashState.IN_TRASH,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context(), CribaDatabase::class.java).build()
        actionLog = RoomActionLog(database.actionLogDao()) { 1_000L }
        trashIndex = RoomTrashIndex(database.trashDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun beginYCompleteRegistranElResultado() = runBlocking<Unit> {
        val id = actionLog.begin(ActionType.MOVED_TO_TRASH, "/storage/emulated/0/Download/a.zip", "t-1", 100)

        actionLog.complete(id, ActionResult.SUCCESS, "movido")

        val record = actionLog.recent().single()
        assertEquals(ActionResult.SUCCESS, record.result)
        assertEquals("movido", record.message)
        assertEquals(1_000L, record.atEpochMillis)
        assertEquals(100L, record.bytes)
    }

    @Test
    fun recentDevuelveLosMasRecientesPrimero() = runBlocking<Unit> {
        var now = 0L
        val log = RoomActionLog(database.actionLogDao()) { now }
        now = 10
        log.begin(ActionType.MOVED_TO_TRASH, "/a", null, 1)
        now = 20
        log.begin(ActionType.MOVED_TO_TRASH, "/b", null, 2)
        now = 30
        log.begin(ActionType.MOVED_TO_TRASH, "/c", null, 3)

        assertEquals(listOf("/c", "/b", "/a"), log.recent().map { it.originalPath })
        assertEquals(listOf("/c", "/b"), log.recent(limit = 2).map { it.originalPath })
    }

    @Test
    fun addConIdDuplicadoLanzaIllegalStateException() = runBlocking<Unit> {
        trashIndex.add(item("t-1"))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { trashIndex.add(item("t-1")) }
        }
    }

    @Test
    fun setStateYInTrashBytes() = runBlocking<Unit> {
        trashIndex.add(item("t-1", bytes = 100))
        trashIndex.add(item("t-2", bytes = 200))

        trashIndex.setState("t-2", TrashState.RESTORED)

        assertEquals(100L, trashIndex.inTrashBytes())
        assertEquals(listOf("t-1"), trashIndex.inTrash().map { it.id })
        assertNotNull(trashIndex.get("t-2"))
        assertEquals(TrashState.RESTORED, trashIndex.get("t-2")?.state)
    }

    @Test
    fun losRegistrosSiguenTrasReabrirLaBaseDeDatos() = runBlocking<Unit> {
        val name = "criba-test-${System.nanoTime()}.db"
        var fileDatabase = Room.databaseBuilder(context(), CribaDatabase::class.java, name).build()
        val id = RoomActionLog(fileDatabase.actionLogDao()) { 5L }
            .begin(ActionType.MOVED_TO_TRASH, "/a", null, 10)
        RoomActionLog(fileDatabase.actionLogDao()) { 5L }.complete(id, ActionResult.FAILED, "fallo")
        fileDatabase.close()

        fileDatabase = Room.databaseBuilder(context(), CribaDatabase::class.java, name).build()
        val record = RoomActionLog(fileDatabase.actionLogDao()) { 5L }.recent().single()
        assertEquals(ActionResult.FAILED, record.result)
        assertEquals("fallo", record.message)
        fileDatabase.close()
        context().deleteDatabase(name)
    }

    @Test
    fun elDaoDelRegistroNoExponeBorrado() {
        val metodoNames = ActionLogDao::class.java.declaredMethods.map { it.name.lowercase() }

        assertTrue(metodoNames.none { it.contains("delete") || it.contains("clear") })
    }
}
