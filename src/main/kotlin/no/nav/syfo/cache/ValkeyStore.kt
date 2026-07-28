package no.nav.syfo.cache

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.tilgang.Tilgang
import no.nav.syfo.util.configuredJacksonMapper
import org.slf4j.LoggerFactory
import redis.clients.jedis.*
import redis.clients.jedis.exceptions.JedisConnectionException

class ValkeyStore(
    private val jedisPool: JedisPool,
) : IValkeyStore {
    override val objectMapper: ObjectMapper = configuredJacksonMapper()

    override fun get(
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

    override fun mget(keys: List<String>): List<String?> {
        return try {
            jedisPool.resource.use { jedis ->
                jedis.mget(*keys.toTypedArray())
            }
        } catch (e: JedisConnectionException) {
            log.warn("Got connection error when fetching from valkey! Continuing without cached value", e)
            emptyList()
        }
    }

    override fun <T> setObject(
        key: String,
        value: T,
        expireSeconds: Long,
    ) {
        val valueJson = objectMapper.writeValueAsString(value)
        set(key, valueJson, expireSeconds)
    }

    override fun getObjects(keys: List<String>): Map<String, Tilgang?> {
        if (keys.isEmpty()) return emptyMap()
        val values = mget(keys)
        return keys.zip(
            values.map { value ->
                value?.let { objectMapper.readValue(it, Tilgang::class.java) }
            }
        ).toMap()
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
