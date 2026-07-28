package no.nav.syfo.cache

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.tilgang.Tilgang

interface IValkeyStore {
    val objectMapper: ObjectMapper
    fun get(key: String): String?
    fun mget(keys: List<String>): List<String?>
    fun <T> setObject(key: String, value: T, expireSeconds: Long)

    /**
     * Fetches values for [keys] with a single mget.
     *
     * @return A map where each entry is (key -> deserialized value) or (key -> null) on cache miss.
     */
    fun getObjects(keys: List<String>): Map<String, Tilgang?>
}

inline fun <reified T> IValkeyStore.getObject(key: String): T? {
    return get(key)?.let { objectMapper.readValue(it, T::class.java) }
}

inline fun <reified T> IValkeyStore.getListObject(key: String): List<T>? {
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
