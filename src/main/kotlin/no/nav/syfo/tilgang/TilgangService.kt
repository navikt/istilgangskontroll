package no.nav.syfo.tilgang

import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.domain.Personident

class TilgangService(
    val graphApiClient: GraphApiClient,
    val axsysClient: AxsysClient,
    val adRoller: AdRoller,
    val redisStore: RedisStore,
) {

    suspend fun hasTilgangToSyfo(token: Token, callId: String): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederIdent"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        val tilgang = Tilgang(
            erGodkjent = graphApiClient.hasAccess(
                adRolle = adRoller.SYFO,
                token = token,
                callId = callId,
            )
        )
        redisStore.setObject(
            key = cacheKey,
            value = tilgang,
            expireSeconds = TWELVE_HOURS_IN_SECS
        )
        return tilgang
    }

    suspend fun hasTilgangToEnhet(token: Token, callId: String, enhet: Enhet): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_ENHET_PREFIX$veilederIdent-$enhet"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }
        val enheter = axsysClient.getEnheter(token = token, callId = callId)
        val tilgang = Tilgang(
            erGodkjent = enheter.map { it.enhetId }.contains(enhet.id)
        )
        redisStore.setObject(
            key = cacheKey,
            value = tilgang,
            expireSeconds = TWELVE_HOURS_IN_SECS
        )
        return tilgang
    }

    fun hasTilgangToPerson(token: Token, personident: Personident, callId: String): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        // TODO:
        //  - TilgangTilTjenesten
        //  - GeografiskTilgang (Skiller seg fra den andre enhetstilgangen, fordi man her sjekker nasjonal/regional)
        //      - Nasjonal tilgang
        //      - Lokal tilgang til enhet
        //      - regional tilgang til enhet
        //  - Hvis kode6, sjekk tilgang
        //  - Hvis kode7, sjekk tilgang
        //  - Hvis skjermet, sjekk tilgang

        val tilgang = Tilgang(erGodkjent = false)

        redisStore.setObject(
            key = cacheKey,
            value = tilgang,
            expireSeconds = TWELVE_HOURS_IN_SECS
        )
        return tilgang
    }

    companion object {
        const val TILGANG_TIL_TJENESTEN_PREFIX = "tilgang-til-tjenesten-"
        const val TILGANG_TIL_ENHET_PREFIX = "tilgang-til-enhet-"
        const val TILGANG_TIL_PERSON_PREFIX = "tilgang-til-person-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
