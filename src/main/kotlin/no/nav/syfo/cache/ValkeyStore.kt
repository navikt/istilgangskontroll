package no.nav.syfo.application.cache

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.tilgang.Tilgang
import no.nav.syfo.util.configuredJacksonMapper
import org.slf4j.LoggerFactory
import redis.clients.jedis.*
import redis.clients.jedis.exceptions.JedisConnectionException

class ValkeyStore(
    private val jedisPool: JedisPool,
) {
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    inline fun <reified T> getObject(
        key: String,
    ): T? {
        return get(key)?.let { it ->
            objectMapper.readValue(it, T::class.java)
        }
    }

    inline fun <reified T> getListObject(key: String): List<T>? {
        val value = get(key)
        return if (value != null) {
            objectMapper.readValue(
                value,
                objectMapper.typeFactory.constructCollectionType(ArrayList::class.java, T::class.java)
            )
        } else {
            null
        }
    }

    /**
     * Fetches values for [keys] with a single mget.
     *
     * @return A map where each entry is (key -> deserialized value) or (key -> null) on cache miss.
     */
    fun getObjects(keys: List<String>): Map<String, Tilgang?> {
        if (keys.isEmpty()) return emptyMap()
        val values = mget(keys)
        return keys.zip(
            values.map { value ->
                value?.let { objectMapper.readValue(it, Tilgang::class.java) }
            }
        ).toMap()
    }

    fun get(
        key: String,
    ): String? {
        try {
            jedisPool.resource.use { jedis ->
                return jedis.get(key)
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from valkey! Continuing without cached value", e)
            return null
        }
    }

    fun mget(keys: List<String>): List<String?> {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.mget(*keys.toTypedArray())
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from valkey! Continuing without cached value", e)
            emptyList()
        }
    }

    fun <T> setObject(
        key: String,
        value: T,
        expireSeconds: Long,
    ) {
        val valueJson = objectMapper.writeValueAsString(value)
        set(key, valueJson, expireSeconds)
    }

    private fun set(
        key: String,
        value: String,
        expireSeconds: Long,
    ) {
        try {
            jedisPool.resource.use { jedis ->
                jedis.setex(
                    key,
                    expireSeconds,
                    value,
                )
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when storing in valkey! Continue without caching", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ValkeyStore::class.java)
    }
}
