package no.nav.syfo.application.cache

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.tilgang.Tilgang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

class ValkeyStoreTest {

    val jedisPool = mockk<JedisPool>()
    val jedis = mockk<Jedis>()
    val store = ValkeyStore(jedisPool)

    @Test
    fun `getObjects returns key to value pairs and null on cache miss`() {
        every { jedisPool.resource } returns jedis
        every { jedis.close() } returns Unit

        every { jedis.mget("k1", "k2", "k3") } returns listOf(
            """{"erGodkjent":true}""",
            null,
            """{"erGodkjent":false}""",
        )

        val store = ValkeyStore(jedisPool)

        val result = store.getObjects(listOf("k1", "k2", "k3"))

        assertEquals(
            mapOf(
                "k1" to Tilgang(erGodkjent = true),
                "k2" to null,
                "k3" to Tilgang(erGodkjent = false),
            ),
            result,
        )

        verify(exactly = 1) { jedis.close() }
    }

    @Test
    fun `getObjects handles empty keys`() {
        val result = store.getObjects(emptyList())

        assertEquals(emptyMap<String, Tilgang?>(), result)
    }
}
