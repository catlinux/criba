package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FakeActionLogTest {
    private fun log(vararg times: Long): FakeActionLog {
        var index = 0
        return FakeActionLog { times.getOrElse(index++) { times.lastOrNull() ?: 0L } }
    }

    @Test
    fun `begin crea un registro pendiente con id creciente`() = runSuspend {
        val log = log(10, 20)

        val first = log.begin(ActionType.MOVED_TO_TRASH, "/a", "t1", 100)
        val second = log.begin(ActionType.RESTORED, "/b", null, 200)

        assertEquals(1L, first)
        assertEquals(2L, second)
        val record = log.recent().first { it.id == first }
        assertEquals(ActionResult.PENDING, record.result)
    }

    @Test
    fun `complete actualiza el resultado y el mensaje`() = runSuspend {
        val log = log(10)
        val id = log.begin(ActionType.MOVED_TO_TRASH, "/a", "t1", 100)

        log.complete(id, ActionResult.SUCCESS, "movido")

        val record = log.recent().single()
        assertEquals(ActionResult.SUCCESS, record.result)
        assertEquals("movido", record.message)
    }

    @Test
    fun `complete rechaza PENDING`() {
        assertThrows(IllegalArgumentException::class.java) {
            runSuspend {
                val log = log(10)
                val id = log.begin(ActionType.MOVED_TO_TRASH, "/a", "t1", 100)
                log.complete(id, ActionResult.PENDING)
            }
        }
    }

    @Test
    fun `recent ordena de mas reciente a mas antiguo y respeta el limite`() = runSuspend {
        val log = log(10, 20, 30)
        log.begin(ActionType.MOVED_TO_TRASH, "/a", null, 1)
        log.begin(ActionType.MOVED_TO_TRASH, "/b", null, 2)
        log.begin(ActionType.MOVED_TO_TRASH, "/c", null, 3)

        assertEquals(listOf("/c", "/b", "/a"), log.recent().map { it.originalPath })
        assertEquals(listOf("/c", "/b"), log.recent(limit = 2).map { it.originalPath })
    }
}
