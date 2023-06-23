package no.nav.syfo.tilgang

import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.util.getNAVIdentFromToken

class TilgangService(
    val graphApiClient: GraphApiClient,
    val adRoller: AdRoller,
    val redisStore: RedisStore,
) {

    suspend fun sjekkTilgangTilTjenesten(token: String): Tilgang {
        val veilederIdent = getNAVIdentFromToken(token)
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederIdent"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        val tilgang = Tilgang(
            harTilgang = graphApiClient.hasAccess(adRoller.SYFO, token)
        )
        redisStore.setObject(
            key = cacheKey,
            value = tilgang,
            expireSeconds = TWELVE_HOURS_IN_SECS
        )
        return tilgang
    }

    companion object {
        const val TILGANG_TIL_TJENESTEN_PREFIX = "tilgang-til-tjenesten-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
