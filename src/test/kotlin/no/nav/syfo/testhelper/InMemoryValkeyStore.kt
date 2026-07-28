package no.nav.syfo.testhelper

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.cache.IValkeyStore
import no.nav.syfo.tilgang.Tilgang
import no.nav.syfo.util.configuredJacksonMapper
import java.util.concurrent.ConcurrentHashMap

class InMemoryValkeyStore : IValkeyStore {
    private val store = ConcurrentHashMap<String, String>()
    override val objectMapper: ObjectMapper = configuredJacksonMapper()

    override fun get(key: String): String? = store[key]

    override fun mget(keys: List<String>): List<String?> = keys.map { store[it] }

    override fun <T> setObject(key: String, value: T, expireSeconds: Long) {
        store[key] = objectMapper.writeValueAsString(value)
    }

    override fun getObjects(keys: List<String>): Map<String, Tilgang?> {
        if (keys.isEmpty()) return emptyMap()
        return keys.associateWith { key ->
            get(key)?.let { objectMapper.readValue(it, Tilgang::class.java) }
        }
    }

    fun clear() {
        store.clear()
    }
}
