package no.nav.syfo.tilgang

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.axsys.AxsysClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.client.pdl.isKode6
import no.nav.syfo.client.pdl.isKode7
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.domain.Personident
import org.slf4j.LoggerFactory

class TilgangService(
    val graphApiClient: GraphApiClient,
    val axsysClient: AxsysClient,
    val skjermedePersonerPipClient: SkjermedePersonerPipClient,
    val pdlClient: PdlClient,
    val behandlendeEnhetClient: BehandlendeEnhetClient,
    val norgClient: NorgClient,
    val adRoller: AdRoller,
    val redisStore: RedisStore,
) {

    private suspend fun hasAccessToSYFO(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(
            adRolle = adRoller.SYFO,
            token = token,
            callId = callId,
        )
    }

    suspend fun hasTilgangToSyfo(token: Token, callId: String): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederIdent"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        val tilgang = Tilgang(
            erGodkjent = hasAccessToSYFO(
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

    private suspend fun hasNasjonalAccess(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(adRolle = adRoller.NASJONAL, token = token, callId = callId)
    }

    private suspend fun hasRegionalAccess(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(adRolle = adRoller.REGIONAL, token = token, callId = callId)
    }

    private suspend fun veiledersOverordnedeEnheter(enheter: List<Enhet>, callId: String): List<Enhet> {
        val overordnedeEnheter = enheter.map {
            norgClient.getOverordnetEnhetListForNAVKontor(callId = callId, enhet = it)
                .map { overordnetEnhet -> Enhet(overordnetEnhet.enhetNr) }
        }.flatten()

        return overordnedeEnheter
    }

    private suspend fun innbyggersOverordnedeEnheter(enhet: Enhet, callId: String): List<Enhet> {
        val overordnedeEnheter = norgClient.getOverordnetEnhetListForNAVKontor(callId = callId, enhet = enhet)
            .map { overordnetEnhet -> Enhet(overordnetEnhet.enhetNr) }

        return overordnedeEnheter
    }

    private suspend fun isGeografiskAccessGodkjent(
        callId: String,
        personident: Personident,
        token: Token,
    ): Boolean {
        if (hasNasjonalAccess(token = token, callId = callId)) {
            return true
        }

        val behandlendeEnhetDTO = behandlendeEnhetClient.getEnhetWithOboToken(
            callId = callId,
            personident = personident,
            token = token,
        )
        val behandlendeEnhet = Enhet(behandlendeEnhetDTO.enhetId)

        val veiledersEnheter = axsysClient.getEnheter(token = token, callId = callId).map { Enhet(it.enhetId) }
        val hasAccessToLokalEnhet = veiledersEnheter.map { it.id }.contains(behandlendeEnhet.id)

        if (hasAccessToLokalEnhet) {
            return true
        }

        if (hasRegionalAccess(token = token, callId = callId)) {
            val veiledersOverordnedeEnheter = veiledersOverordnedeEnheter(enheter = veiledersEnheter, callId = callId)
            val innbyggersOverordnedeEnheter = innbyggersOverordnedeEnheter(enhet = behandlendeEnhet, callId = callId)

            return innbyggersOverordnedeEnheter.any { it in veiledersOverordnedeEnheter }
        }

        return false
    }

    private suspend fun isKode6AccessAvslatt(token: Token, callId: String): Boolean {
        return !graphApiClient.hasAccess(adRolle = adRoller.KODE6, token = token, callId = callId)
    }

    private suspend fun isKode7AccessAvslatt(token: Token, callId: String): Boolean {
        return !graphApiClient.hasAccess(adRolle = adRoller.KODE7, token = token, callId = callId)
    }

    private suspend fun isAdressebeskyttelseAccessGodkjent(
        callId: String,
        personident: Personident,
        token: Token,
    ): Boolean {
        val person = pdlClient.getPersonWithOboToken(
            callId = callId,
            personident = personident,
            token = token,
        )

        return if (person.isKode6() && isKode6AccessAvslatt(token = token, callId = callId)) {
            false
        } else if (person.isKode7() && isKode7AccessAvslatt(token = token, callId = callId)) {
            false
        } else {
            true
        }
    }

    private suspend fun isSkjermetAccessGodkjent(callId: String, personident: Personident, token: Token): Boolean {
        val personIsSkjermet = skjermedePersonerPipClient.getIsSkjermetWithOboToken(
            callId = callId,
            personIdent = personident,
            token = token,
        )

        return if (!personIsSkjermet) {
            true
        } else {
            graphApiClient.hasAccess(
                adRolle = adRoller.EGEN_ANSATT,
                token = token,
                callId = callId,
            )
        }
    }

    suspend fun hasTilgangToPerson(token: Token, personident: Personident, callId: String): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
        val cachedTilgang: Tilgang? = redisStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        val erGodkjent = if (!hasAccessToSYFO(callId = callId, token = token)) {
            false
        } else if (!isGeografiskAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else if (!isSkjermetAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else if (!isAdressebeskyttelseAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else {
            true
        }
        val tilgang = Tilgang(erGodkjent = erGodkjent)

        redisStore.setObject(
            key = cacheKey,
            value = tilgang,
            expireSeconds = TWELVE_HOURS_IN_SECS
        )
        return tilgang
    }

    suspend fun filterIdenterByVeilederAccess(callId: String, token: Token, personidenter: List<String>): List<String> {
        return personidenter.filter { personident ->
            hasTilgangToPerson(
                token = token,
                personident = Personident(personident),
                callId = callId,
            ).erGodkjent
        }
    }

    private suspend fun preloadPersonInfoCache(callId: String, personident: Personident) = coroutineScope {
        try {
            val behandlendeEnhetClient = async {
                behandlendeEnhetClient.getEnhetWithSystemToken(
                    callId = callId,
                    personident = personident,
                )
            }

            val isSkjermet = async {
                skjermedePersonerPipClient.getIsSkjermetWithSystemToken(
                    callId = callId,
                    personIdent = personident,
                )
            }

            val person = async {
                pdlClient.getPersonWithSystemToken(
                    callId = callId,
                    personident = personident,
                )
            }

            listOf(behandlendeEnhetClient, isSkjermet, person).awaitAll()
        } catch (e: Exception) {
            log.error("Failed to preload cache callId=$callId", e)
        }
    }

    suspend fun preloadCacheForPersonAccess(callId: String, personidenter: List<String>) = coroutineScope {
        val result = personidenter.map { personident ->
            async {
                preloadPersonInfoCache(callId = callId, personident = Personident(personident))
            }
        }
        result.awaitAll()
    }

    companion object {
        private val log = LoggerFactory.getLogger(TilgangService::class.java)

        const val TILGANG_TIL_TJENESTEN_PREFIX = "tilgang-til-tjenesten-"
        const val TILGANG_TIL_ENHET_PREFIX = "tilgang-til-enhet-"
        const val TILGANG_TIL_PERSON_PREFIX = "tilgang-til-person-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
