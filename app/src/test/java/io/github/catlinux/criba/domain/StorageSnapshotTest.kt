package io.github.catlinux.criba.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSnapshotTest {
    @Test
    fun `usado es total menos libre`() {
        assertEquals(40L, StorageSnapshot(totalBytes = 100, freeBytes = 60).usedBytes)
    }
}
