package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashSelectionTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun item(
        id: String,
        bytes: Long = 100,
        trashedAt: Long = now,
        state: TrashState = TrashState.IN_TRASH,
    ) = TrashItem(id, "/original/$id", "/trash/$id", bytes, null, trashedAt, state)

    @Test
    fun `justo en el vencimiento quedan cero dias`() {
        assertEquals(0, TrashRetention.daysLeft(item("x", trashedAt = now), now + 30 * day))
    }

    @Test
    fun `un milisegundo antes del vencimiento queda un dia`() {
        assertEquals(1, TrashRetention.daysLeft(item("x", trashedAt = now), now + 30 * day - 1))
    }

    @Test
    fun `un elemento recien movido tiene treinta dias`() {
        assertEquals(30, TrashRetention.daysLeft(item("x", trashedAt = now), now))
    }

    @Test
    fun `un elemento vencido tiene cero dias`() {
        assertEquals(0, TrashRetention.daysLeft(item("x", trashedAt = now - 31 * day), now))
    }

    @Test
    fun `daysLeft admite una retencion distinta`() {
        assertEquals(7, TrashRetention.daysLeft(item("x", trashedAt = now), now, retentionDays = 7))
    }

    @Test
    fun `toggle selecciona y deselecciona`() {
        val initial = TrashSelection(listOf(item("a")))

        assertEquals(setOf("a"), initial.toggle("a").selectedIds)
        assertTrue(initial.toggle("a").toggle("a").selectedIds.isEmpty())
    }

    @Test
    fun `toggle ignora ids inexistentes`() {
        val initial = TrashSelection(listOf(item("a")))

        assertTrue(initial.toggle("missing") === initial)
    }

    @Test
    fun `ignora elementos que ya no estan en la papelera`() {
        val selection = TrashSelection(
            listOf(item("inside"), item("restored", state = TrashState.RESTORED), item("purged", state = TrashState.PURGED)),
            selectedIds = setOf("inside", "restored", "missing"),
        )

        assertEquals(listOf("inside"), selection.items.map { it.id })
        assertEquals(setOf("inside"), selection.selectedIds)
    }

    @Test
    fun `selectAll marca todos los elementos en la papelera`() {
        val selection = TrashSelection(listOf(item("a"), item("b")))

        assertEquals(setOf("a", "b"), selection.selectAll().selectedIds)
    }

    @Test
    fun `clear quita toda la seleccion`() {
        val selection = TrashSelection(listOf(item("a"))).selectAll()

        assertTrue(selection.clear().selectedIds.isEmpty())
    }

    @Test
    fun `bytes y cantidad seleccionados son reales y conservan orden`() {
        val selection = TrashSelection(listOf(item("a", 300), item("b", 200), item("c", 900))).selectAll()
        val withoutC = selection.toggle("c")

        assertEquals(500L, withoutC.selectedBytes)
        assertEquals(2, withoutC.selectedCount)
        assertEquals(listOf("a", "b"), withoutC.selectedItems.map { it.id })
    }

    @Test
    fun `confirmPurge devuelve null sin seleccion`() {
        assertNull(TrashSelection(listOf(item("a"))).confirmPurge())
    }

    @Test
    fun `confirmPurge conserva orden y bytes reales`() {
        val selection = TrashSelection(listOf(item("a", 300), item("b", 200))).selectAll()

        val confirmed = selection.confirmPurge()!!

        assertEquals(500L, confirmed.totalBytes)
        assertEquals(listOf("a", "b"), confirmed.items.map { it.id })
    }

    @Test
    fun `la seleccion no puede cambiar al mutar las listas de entrada`() {
        val items = mutableListOf(item("a"))
        val ids = mutableSetOf("a")
        val selection = TrashSelection(items, ids)
        items.clear()
        ids.clear()

        assertEquals(listOf("a"), selection.items.map { it.id })
        assertEquals(setOf("a"), selection.selectedIds)
        assertFalse(selection.clear().selectedIds.contains("a"))
    }
}
