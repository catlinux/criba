package io.github.catlinux.criba.domain

/** Días enteros desde el último uso; null si no hay dato y 0 si la fecha está en el futuro. */
fun lastUseDaysAgo(nowEpochSeconds: Long, lastUsedEpochSeconds: Long?): Long? {
    val lastUsed = lastUsedEpochSeconds ?: return null
    val difference = nowEpochSeconds - lastUsed
    return if (difference <= 0) 0 else difference / 86_400L
}
