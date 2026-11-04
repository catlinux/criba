package io.github.catlinux.criba.data.db

import io.github.catlinux.criba.domain.ActionRecord
import io.github.catlinux.criba.domain.ActionResult
import io.github.catlinux.criba.domain.ActionType
import io.github.catlinux.criba.domain.TrashItem
import io.github.catlinux.criba.domain.TrashState
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    @Test
    fun `ida y vuelta de acciones con todos los enums`() {
        for (type in ActionType.entries) {
            for (result in ActionResult.entries) {
                val record = ActionRecord(
                    id = 7,
                    atEpochMillis = 1_700_000_000_000,
                    type = type,
                    originalPath = "/storage/emulated/0/Download/a.zip",
                    trashId = "t-1",
                    bytes = 1234,
                    result = result,
                    message = "mensaje",
                )

                assertEquals(record, record.toEntity().toDomain())
            }
        }
    }

    @Test
    fun `ida y vuelta de acciones con campos opcionales nulos`() {
        val record = ActionRecord(
            id = 1,
            atEpochMillis = 10,
            type = ActionType.PURGED,
            originalPath = "/x",
            trashId = null,
            bytes = 0,
            result = ActionResult.PENDING,
            message = null,
        )

        assertEquals(record, record.toEntity().toDomain())
    }

    @Test
    fun `ida y vuelta de la papelera con todos los estados`() {
        for (state in TrashState.entries) {
            val item = TrashItem(
                id = "t-1",
                originalPath = "/storage/emulated/0/Download/a.zip",
                trashPath = "/storage/emulated/0/.criba-trash/t-1",
                bytes = 512,
                sha256 = "abc",
                trashedAtEpochMillis = 1_700_000_000_000,
                state = state,
            )

            assertEquals(item, item.toEntity().toDomain())
        }
    }

    @Test
    fun `ida y vuelta de la papelera con sha nulo`() {
        val item = TrashItem(
            id = "t-2",
            originalPath = "/x",
            trashPath = "/y",
            bytes = 0,
            sha256 = null,
            trashedAtEpochMillis = 1,
            state = TrashState.IN_TRASH,
        )

        assertEquals(item, item.toEntity().toDomain())
    }
}
