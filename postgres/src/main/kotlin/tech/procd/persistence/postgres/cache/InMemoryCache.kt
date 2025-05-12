package tech.procd.persistence.postgres.cache

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class InMemoryCache<K : Any, V>(
    private val loadFn: suspend (K) -> V?,
    private val ttl: Duration = Duration.ofHours(DEFAULT_DURATION)
) {
    companion object {
        private const val DEFAULT_DURATION = 1L
    }

    private val cache = ConcurrentHashMap<K, CachedValue<V>>()
    private val maxAge: Long
        get() = System.currentTimeMillis() + ttl.toMillis()

    suspend fun get(key: K): V? =
        cache.getOrPut(key) { CachedValue(loadFn(key), maxAge) }?.let {
            if (it.maxAge > System.currentTimeMillis()) {
                it.value
            } else {
                cache.put(key, CachedValue(loadFn(key), maxAge))?.value
            }
        }

    fun remove(key: K) {
        cache.remove(key)
    }

    private data class CachedValue<V>(val value: V?, val maxAge: Long)
}
