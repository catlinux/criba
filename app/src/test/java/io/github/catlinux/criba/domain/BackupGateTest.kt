package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupGateTest {
    private fun file(size: Long = 100, modified: Long = 5) =
        ScanFile("a.jpg", "a.jpg", size, modified, "DCIM/")

    private fun record(size: Long = 100, modified: Long = 5, sha: String = "hash", ref: String = "dest/a.jpg") =
        BackupRecord("/src/a.jpg", size, modified, sha, ref, "Copia", 1)

    private class FakeIndex(private val record: BackupRecord?) : BackupIndex {
        override suspend fun add(record: BackupRecord) = Unit
        override suspend fun find(sourcePath: String): BackupRecord? =
            record?.takeIf { it.sourcePath == sourcePath }
    }

    private class FakeTransport : BackupTransport {
        var sourceHash: String? = "hash"
        var destinationSize: Long? = 100
        var destinationHash: String? = "hash"
        override val label = "Copia"

        override suspend fun existingNames(): Set<String> = emptySet()
        override suspend fun copyFile(
            file: ScanFile,
            sourcePath: String,
            destinationName: String,
            onBytes: (Long) -> Unit,
        ): BackupCopyResult? = null

        override suspend fun sha256OfDestination(destinationRef: String): String? = destinationHash
        override suspend fun sizeOfDestination(destinationRef: String): Long? = destinationSize
        override suspend fun discardPartial(destinationRef: String) = Unit
        override suspend fun sha256OfSource(sourcePath: String, expectedSize: Long): String? = sourceHash
    }

    private fun gate(record: BackupRecord?, transport: FakeTransport = FakeTransport()) =
        BackupGate(transport, FakeIndex(record)) to transport

    @Test
    fun `sin registro queda bloqueado`() = runSuspend {
        val (gate, _) = gate(null)

        assertEquals(GateResult.Locked(GateReason.NO_BACKUP), gate.reverify(file(), "/src/a.jpg"))
    }

    @Test
    fun `si el tamano o la fecha cambiaron el origen cambio`() = runSuspend {
        val (gateSize, _) = gate(record(size = 99))
        val (gateDate, _) = gate(record(modified = 4))

        assertEquals(GateResult.Locked(GateReason.SOURCE_CHANGED), gateSize.reverify(file(), "/src/a.jpg"))
        assertEquals(GateResult.Locked(GateReason.SOURCE_CHANGED), gateDate.reverify(file(), "/src/a.jpg"))
    }

    @Test
    fun `si el hash del origen no coincide queda bloqueado`() = runSuspend {
        val (gateNull, transportNull) = gate(record())
        transportNull.sourceHash = null
        val (_, transportDifferent) = gate(record())
        transportDifferent.sourceHash = "otro"


        assertEquals(GateResult.Locked(GateReason.SOURCE_HASH_DIFFERS), gateNull.reverify(file(), "/src/a.jpg"))
        assertEquals(
            GateResult.Locked(GateReason.SOURCE_HASH_DIFFERS),
            gate(record(), transportDifferent).first.reverify(file(), "/src/a.jpg"),
        )
    }

    @Test
    fun `si falta el destino queda bloqueado`() = runSuspend {
        val (_, transportSize) = gate(record())
        transportSize.destinationSize = null
        val (_, transportHash) = gate(record())
        transportHash.destinationHash = null

        assertEquals(
            GateResult.Locked(GateReason.DESTINATION_MISSING),
            gate(record(), transportSize).first.reverify(file(), "/src/a.jpg"),
        )
        assertEquals(
            GateResult.Locked(GateReason.DESTINATION_MISSING),
            gate(record(), transportHash).first.reverify(file(), "/src/a.jpg"),
        )
    }

    @Test
    fun `si el destino cambio queda bloqueado`() = runSuspend {
        val (_, transportSize) = gate(record())
        transportSize.destinationSize = 50
        val (_, transportHash) = gate(record())
        transportHash.destinationHash = "otro"

        assertEquals(
            GateResult.Locked(GateReason.DESTINATION_HASH_DIFFERS),
            gate(record(), transportSize).first.reverify(file(), "/src/a.jpg"),
        )
        assertEquals(
            GateResult.Locked(GateReason.DESTINATION_HASH_DIFFERS),
            gate(record(), transportHash).first.reverify(file(), "/src/a.jpg"),
        )
    }

    @Test
    fun `si todo coincide queda desbloqueado con su registro`() = runSuspend {
        val expected = record()
        val (gate, _) = gate(expected)

        val result = gate.reverify(file(), "/src/a.jpg")

        assertEquals(GateResult.Unlocked(expected), result)
    }

    @Test
    fun `hasRecord exige tambien tamano y fecha`() = runSuspend {
        val (gate, _) = gate(record())

        assertTrue(gate.hasRecord(file(), "/src/a.jpg"))
        assertFalse(gate.hasRecord(file(size = 200), "/src/a.jpg"))
        assertFalse(gate.hasRecord(file(modified = 6), "/src/a.jpg"))
        assertFalse(gate.hasRecord(file(), "/otro"))
    }

    @Test
    fun `unlockedFiles solo devuelve los desbloqueados`() = runSuspend {
        val (gate, _) = gate(record())
        val good = BackupItem(file(), "/src/a.jpg")
        val missing = BackupItem(file(), "/src/b.jpg")

        assertEquals(setOf("/src/a.jpg"), gate.unlockedFiles(listOf(good, missing)))
    }
}
