package io.github.catlinux.criba.data.apps

import io.github.catlinux.criba.domain.AppSizes

data class UsageRecord(val packageName: String, val lastTimeUsedMillis: Long)

/** Android devuelve 0 cuando no hay dato; esos registros no cuentan como uso. */
fun latestUsePerPackage(records: List<UsageRecord>): Map<String, Long> =
    records
        .filter { it.lastTimeUsedMillis > 0 }
        .groupBy { it.packageName }
        .mapValues { (_, values) -> values.maxOf { it.lastTimeUsedMillis } / 1000 }

/**
 * En StorageStats, dataBytes ya incluye cacheBytes; se resta para que AppSizes.totalBytes
 * (app + data + cache) no cuente la caché dos veces.
 */
fun toAppSizes(appBytes: Long, dataBytes: Long, cacheBytes: Long): AppSizes {
    val cache = cacheBytes.coerceAtLeast(0)
    return AppSizes(
        appBytes = appBytes.coerceAtLeast(0),
        dataBytes = (dataBytes - cache).coerceAtLeast(0),
        cacheBytes = cache,
    )
}
