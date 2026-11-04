package io.github.catlinux.criba.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupVerifierTest {
    private fun file(name: String = "a.jpg", size: Long = 100, modified: Long = 5) =
        ScanFile(id = name, displayName = name, sizeBytes = size, modifiedEpochSeconds = modified, relativePath = "DCIM/")

    private fun item(file: ScanFile = file()) = BackupItem(file, "/src/${file.displayName}")

    private fun verifier(
        transport: FakeTransport,
        index: FakeIndex = FakeIndex(),
        now: Long = 999,
    ) = BackupVerifier(transport, index) { now }

    private class FakeIndex : BackupIndex {
        val records = mutableListOf<BackupRecord>()
        var throwOnAdd = false

        override suspend fun add(record: BackupRecord) {
            if (throwOnAdd) throw IllegalStateException("fallo del índice")
            records += record
        }

        override suspend fun find(sourcePath: String): BackupRecord? =
            records.lastOrNull { it.sourcePath == sourcePath }
    }

    private class FakeTransport : BackupTransport {
        var existing = mutableSetOf<String>()
        var copySha = "hash"
        var copyBytesOffset = 0L
        var copyReturnsNull = false
        var destinationSize: Long? = null
        var destinationSizeByRef: Map<String, Long?> = emptyMap()
        var destinationHash: String? = "hash"
        var destinationHashByRef: Map<String, String?> = emptyMap()
        var sourceHash: String? = "hash"
        var cancelOnName: String? = null
        val copiedNames = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        override val label = "Copia de prueba"

        override suspend fun existingNames(): Set<String> = existing.toSet()

        override suspend fun copyFile(
            file: ScanFile,
            sourcePath: String,
            destinationName: String,
            onBytes: (Long) -> Unit,
        ): BackupCopyResult? {
            copiedNames += destinationName
            if (destinationName == cancelOnName) currentCoroutineContext().cancel()
            if (copyReturnsNull) return null
            onBytes(file.sizeBytes)
            return BackupCopyResult(copySha, file.sizeBytes - copyBytesOffset, "dest/$destinationName")
        }

        override suspend fun sha256OfDestination(destinationRef: String): String? =
            if (destinationRef in destinationHashByRef) destinationHashByRef[destinationRef] else destinationHash

        override suspend fun sizeOfDestination(destinationRef: String): Long? =
            if (destinationRef in destinationSizeByRef) destinationSizeByRef[destinationRef] else destinationSize

        override suspend fun discardPartial(destinationRef: String) {
            discarded += destinationRef
        }

        override suspend fun sha256OfSource(sourcePath: String, expectedSize: Long): String? = sourceHash
    }

    @Test
    fun `una copia correcta queda verificada y registrada`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = 100 }
        val index = FakeIndex()

        val report = verifier(transport, index).backUp(listOf(item()))

        val outcome = report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Verified
        assertEquals("hash", outcome.record.sha256)
        assertEquals("dest/a.jpg", outcome.record.destinationRef)
        assertEquals("Copia de prueba", outcome.record.destinationLabel)
        assertEquals(999L, outcome.record.verifiedAtEpochSeconds)
        assertEquals("/src/a.jpg", index.records.single().sourcePath)
        assertEquals(1, report.verifiedCount)
        assertTrue(transport.discarded.isEmpty())
    }

    @Test
    fun `un hash de destino distinto falla y descarta la copia`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSize = 100
            destinationHash = "otro"
        }
        val index = FakeIndex()

        val report = verifier(transport, index).backUp(listOf(item()))

        assertEquals(BackupFailure.HASH_MISMATCH, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
        assertEquals(listOf("dest/a.jpg"), transport.discarded)
        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `un tamano de destino distinto falla y descarta`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = 50 }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(BackupFailure.SIZE_MISMATCH, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
        assertEquals(listOf("dest/a.jpg"), transport.discarded)
    }

    @Test
    fun `un tamano de copia distinto falla y descarta`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSize = 100
            copyBytesOffset = 10
        }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(BackupFailure.SIZE_MISMATCH, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
        assertEquals(listOf("dest/a.jpg"), transport.discarded)
    }

    @Test
    fun `un destino ilegible falla y descarta`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = null }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(
            BackupFailure.DESTINATION_UNREADABLE,
            (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure,
        )
        assertEquals(listOf("dest/a.jpg"), transport.discarded)
    }

    @Test
    fun `un origen cambiado durante la copia falla y descarta`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSize = 100
            sourceHash = "cambiado"
        }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(BackupFailure.SOURCE_CHANGED, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
        assertEquals(listOf("dest/a.jpg"), transport.discarded)
    }

    @Test
    fun `copyFile null con origen ilegible da SOURCE_UNREADABLE`() = runSuspend {
        val transport = FakeTransport().apply {
            copyReturnsNull = true
            sourceHash = null
        }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(
            BackupFailure.SOURCE_UNREADABLE,
            (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure,
        )
    }

    @Test
    fun `copyFile null con origen legible da COPY_FAILED`() = runSuspend {
        val transport = FakeTransport().apply { copyReturnsNull = true }

        val report = verifier(transport).backUp(listOf(item()))

        assertEquals(BackupFailure.COPY_FAILED, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
    }

    @Test
    fun `un fallo no detiene los demas archivos`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSize = 100
            destinationHashByRef = mapOf("dest/a.jpg" to "malo")
        }
        val good = file("bueno.jpg")
        val items = listOf(item(), BackupItem(good, "/src/bueno.jpg"))

        val report = verifier(transport).backUp(items)

        assertEquals(1, report.failedCount)
        assertEquals(1, report.verifiedCount)
        assertTrue(report.outcomes.getValue("/src/bueno.jpg") is BackupOutcome.Verified)
    }

    @Test
    fun `evita colisiones con nombres existentes`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSize = 100
            existing = mutableSetOf("a.jpg")
        }

        verifier(transport).backUp(listOf(item()))

        assertEquals(listOf("a (1).jpg"), transport.copiedNames)
    }

    @Test
    fun `evita colisiones entre archivos de la misma tanda`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = 100 }
        val items = listOf(item(), item())

        verifier(transport).backUp(items)

        assertEquals(listOf("a.jpg", "a (1).jpg"), transport.copiedNames)
    }

    @Test
    fun `la cancelacion se propaga y conserva lo ya registrado`() = runBlocking {
        val transport = FakeTransport().apply {
            destinationSize = 100
            cancelOnName = "a (1).jpg"
        }
        val index = FakeIndex()
        val job = launch(Dispatchers.Default) {
            verifier(transport, index).backUp(listOf(item(), item()))
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, index.records.size)
    }

    @Test
    fun `informa del progreso sin pasarse del total`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = 100 }
        val progress = mutableListOf<BackupProgress>()
        val other = file("b.jpg", size = 50)

        verifier(transport).backUp(
            listOf(item(), BackupItem(other, "/src/b.jpg")),
            progress::add,
        )

        assertEquals(150L, progress.last().bytesTotal)
        assertEquals(2, progress.last().filesDone)
        assertEquals(150L, progress.last().bytesDone)
        assertTrue(progress.all { it.bytesDone <= it.bytesTotal })
        assertEquals(listOf(0, 1, 2), progress.map { it.filesDone }.distinct())
    }

    @Test
    fun `si el indice falla la copia no se descarta pero no se registra`() = runSuspend {
        val transport = FakeTransport().apply { destinationSize = 100 }
        val index = FakeIndex().apply { throwOnAdd = true }

        val report = verifier(transport, index).backUp(listOf(item()))

        assertEquals(BackupFailure.INDEX_FAILED, (report.outcomes.getValue("/src/a.jpg") as BackupOutcome.Failed).failure)
        assertTrue(transport.discarded.isEmpty())
        assertTrue(index.records.isEmpty())
    }

    @Test
    fun `el informe suma los bytes verificados`() = runSuspend {
        val transport = FakeTransport().apply {
            destinationSizeByRef = mapOf("dest/a.jpg" to 100, "dest/b.jpg" to 50)
        }
        val other = file("b.jpg", size = 50)
        val report = verifier(transport).backUp(listOf(item(), BackupItem(other, "/src/b.jpg")))

        assertEquals(2, report.verifiedCount)
        assertEquals(0, report.failedCount)
        assertEquals(150L, report.verifiedBytes)
    }
}
