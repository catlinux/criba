package io.github.catlinux.criba.data

import android.os.Environment
import android.os.StatFs
import io.github.catlinux.criba.domain.StorageInfoSource
import io.github.catlinux.criba.domain.StorageSnapshot
import javax.inject.Inject

/** Lee el espacio real del almacenamiento interno con StatFs (solo lectura, sin permisos). */
class StatFsStorageInfoSource @Inject constructor() : StorageInfoSource {
    override fun read(): StorageSnapshot {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageSnapshot(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }
}
