package no.nav.syfo.tilgang

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.application.cache.ValkeyStore
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.audit.AuditLogEvent
import no.nav.syfo.audit.CEF
import no.nav.syfo.audit.auditLog
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.behandlendeenhet.BehandlendeEnhetClient
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.norg.NorgClient
import no.nav.syfo.client.pdl.*
import no.nav.syfo.client.skjermedepersoner.SkjermedePersonerPipClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.filterValidPersonidenter
import org.slf4j.LoggerFactory
import kotlin.collections.component1
import kotlin.collections.component2

private const val MAX_BULK_SIZE_TILGANGSMASKIN = 1000

class TilgangService(
    val azureAdClient: AzureAdClient,
    val graphApiClient: GraphApiClient,
    val skjermedePersonerPipClient: SkjermedePersonerPipClient,
    val pdlClient: PdlClient,
    val behandlendeEnhetClient: BehandlendeEnhetClient,
    val norgClient: NorgClient,
    val adRoller: AdRoller,
    val valkeyStore: ValkeyStore,
    val tilgangsmaskin: TilgangsmaskinClient,
) {
    suspend fun hasAccessToSYFO(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(
            adRolle = adRoller.SYFO,
            token = token,
            callId = callId,
        )
    }

    suspend fun checkTilgangToSyfo(token: Token, callId: String): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederIdent"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }

        val tilgang = Tilgang(
            erGodkjent = hasAccessToSYFO(
                token = token,
                callId = callId,
            )
        )
        if (tilgang.erGodkjent) {
            valkeyStore.setObject(
                key = cacheKey,
                value = tilgang,
                expireSeconds = TWELVE_HOURS_IN_SECS
            )
        }
        return tilgang
    }

    suspend fun checkTilgangToEnhet(token: Token, callId: String, enhet: Enhet): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_ENHET_PREFIX$veilederIdent-$enhet"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        if (cachedTilgang != null) {
            return cachedTilgang
        }
        val enheter = graphApiClient.getEnheterForVeileder(token = token, callId = callId)
        val tilgang = Tilgang(
            erGodkjent = enheter.map { it.id }.contains(enhet.id)
        )

        if (tilgang.erGodkjent) {
            valkeyStore.setObject(
                key = cacheKey,
                value = tilgang,
                expireSeconds = TWELVE_HOURS_IN_SECS
            )
        }
        return tilgang
    }

    private suspend fun hasNasjonalAccess(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(adRolle = adRoller.NASJONAL, token = token, callId = callId)
    }

    private suspend fun hasRegionalAccess(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(adRolle = adRoller.REGIONAL, token = token, callId = callId)
    }

    private suspend fun veiledersEnheterOgOverordnedeEnheter(enheter: List<Enhet>, callId: String): List<Enhet> {
        val veiledersEnheter = enheter.toMutableList()
        val overordnedeEnheter = enheter.map {
            norgClient.getOverordnetEnhetListForNAVKontor(callId = callId, enhet = it)
                .map { overordnetEnhet -> Enhet(overordnetEnhet.enhetNr) }
        }.flatten()
        veiledersEnheter.addAll(overordnedeEnheter)

        return veiledersEnheter
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

        val geografiskTilknytning = pdlClient.getPerson(
            callId = callId,
            personident = personident,
        ).geografiskTilknytning?.geografiskTilknytning()

        if (geografiskTilknytning == null) {
            log.warn("Didn't get GT for innbygger, unable to check geografisk access callId=$callId")
            return false
        }

        val innbyggersEnhetNr = try {
            getInnbyggersEnhet(
                callId = callId,
                personident = personident,
                geografiskTilknytning = geografiskTilknytning,
                token = token,
            )
        } catch (exc: Exception) {
            log.warn("Didn't get enhet for innbygger, unable to check geografisk access callId=$callId")
            return false
        }

        val behandlendeEnhet = Enhet(innbyggersEnhetNr)

        val veiledersEnheter = graphApiClient.getEnheterForVeileder(token = token, callId = callId)
        val hasAccessToLokalEnhet = veiledersEnheter.map { it.id }.contains(behandlendeEnhet.id)

        if (hasAccessToLokalEnhet) {
            return true
        }

        if (hasRegionalAccess(token = token, callId = callId)) {
            val veiledersEnheterOgOverordnedeEnheter =
                veiledersEnheterOgOverordnedeEnheter(enheter = veiledersEnheter, callId = callId)
            val innbyggersOverordnedeEnheter = innbyggersOverordnedeEnheter(enhet = behandlendeEnhet, callId = callId)

            return innbyggersOverordnedeEnheter.any { it in veiledersEnheterOgOverordnedeEnheter }
        }

        return false
    }

    private suspend fun getInnbyggersEnhet(
        callId: String,
        personident: Personident,
        geografiskTilknytning: GeografiskTilknytning,
        token: Token,
    ): String {
        return if (geografiskTilknytning.isUtlandOrWithoutGT()) {
            val behandlendeEnhetDTO = behandlendeEnhetClient.getEnhetWithOboToken(
                callId = callId,
                personident = personident,
                token = token,
            )
            behandlendeEnhetDTO.oppfolgingsenhetDTO?.enhet?.enhetId ?: behandlendeEnhetDTO.geografiskEnhet.enhetId
        } else {
            norgClient.getNAVKontorForGT(
                callId = callId,
                geografiskTilknytning = geografiskTilknytning,
            ).enhetNr
        }
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
        val person = pdlClient.getPerson(
            callId = callId,
            personident = personident,
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

    suspend fun checkTilgangToPersonWithPapirsykmelding(
        token: Token,
        personident: Personident,
        callId: String,
        appName: String,
    ): Tilgang {
        return if (hasAccessToPapirsykmelding(token = token, callId = callId)) {
            checkTilgangToPerson(
                token = token,
                personident = personident,
                callId = callId,
                appName = appName,
            )
        } else {
            Tilgang(erGodkjent = false)
        }
    }

    private suspend fun hasAccessToPapirsykmelding(token: Token, callId: String): Boolean {
        return graphApiClient.hasAccess(
            adRolle = adRoller.PAPIRSYKMELDING,
            token = token,
            callId = callId,
        )
    }

    suspend fun checkTilgangToPerson(
        token: Token,
        personident: Personident,
        callId: String,
        appName: String,
        doAuditLog: Boolean = true,
        bulk: Boolean = false,
    ): Tilgang {
        val veilederIdent = token.getNAVIdent()
        val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        val tilgang = cachedTilgang ?: checkTilgangToPersonAndCache(callId, token, personident, cacheKey)
        if (doAuditLog) {
            auditLog(
                CEF(
                    suid = veilederIdent,
                    duid = personident.value,
                    event = AuditLogEvent.Access,
                    permit = tilgang.erGodkjent,
                    appName = appName,
                )
            )
        }
        return tilgang
    }

    suspend fun checkTilgangToPersons(
        token: Token,
        personidenter: List<Personident>,
        callId: String,
        appName: String,
    ): Map<Personident, Tilgang> {
        val veilederIdent = token.getNAVIdent()
        val cacheKeysToPersonident: Map<String, Personident> = personidenter.associate { personident ->
            val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
            cacheKey to personident
        }

        val cacheKeysToValue: Map<String, Tilgang?> =
            valkeyStore.getObjects(keys = cacheKeysToPersonident.keys.toList())

        val (cachedEntries, missingEntries) = cacheKeysToValue.entries.partition { it.value != null }
        val cachedTilganger: Map<Personident, Tilgang> =
            cachedEntries.associate { entry ->
                val personident = cacheKeysToPersonident[entry.key]!!
                personident to entry.value!!
            }
        val uncachedPersonidenter: List<Personident> = missingEntries.map { entry -> cacheKeysToPersonident[entry.key]!! }

        return supervisorScope {
            val hentetTilganger = uncachedPersonidenter.map { personident ->
                async(CHECK_PERSON_TILGANG_DISPATCHER) {
                    val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
                    val tilgang = checkTilgangToPersonAndCache(callId, token, personident, cacheKey)
                    personident to tilgang
                }
            }.awaitAll().toMap()

            val alleTilganger = cachedTilganger + hentetTilganger
            launch { auditLog(tilganger = alleTilganger, veilederIdent = veilederIdent, appName = appName) }
            alleTilganger
        }
    }

    private suspend fun checkTilgangToPersonAndCache(
        callId: String,
        token: Token,
        personident: Personident,
        cacheKey: String,
    ): Tilgang {
        val erGodkjent = if (!isGeografiskAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else if (!isSkjermetAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else if (!isAdressebeskyttelseAccessGodkjent(callId = callId, personident = personident, token = token)) {
            false
        } else {
            true
        }
        val tilgang = Tilgang(erGodkjent = erGodkjent)

        if (tilgang.erGodkjent) {
            valkeyStore.setObject(
                key = cacheKey,
                value = tilgang,
                expireSeconds = TWELVE_HOURS_IN_SECS
            )
        }
        return tilgang
    }

    suspend fun filterIdenterByVeilederAccess(
        callId: String,
        token: Token,
        personidenter: List<String>,
        appName: String,
    ): List<String> {
        if (!hasAccessToSYFO(callId = callId, token = token)) {
            return emptyList()
        }
        preloadOboTokens(callId = callId, token = token)
        val validPersonidenter = personidenter.filterValidPersonidenter()

        return checkTilgangToPersons(token, validPersonidenter, callId, appName)
            .filter { (_, tilgang) -> tilgang.erGodkjent }
            .map { (personident, _) -> personident.value }
    }

    private suspend fun preloadOboTokens(
        callId: String,
        token: Token,
    ) {
        azureAdClient.getOnBehalfOfToken(skjermedePersonerPipClient.clientId, token, callId)
        azureAdClient.getOnBehalfOfToken(behandlendeEnhetClient.clientId, token, callId)
        // pdlClient bruker system token, så trenger ingen OBO-token preloading
        // graphApiClient har implisitt cachet obo-token via hasAccessToSYFO-kallet
    }

    private suspend fun preloadPersonInfoCache(callId: String, personident: Personident) {
        try {
            skjermedePersonerPipClient.getIsSkjermetWithSystemToken(
                callId = callId,
                personIdent = personident,
            )

            pdlClient.getPerson(
                callId = callId,
                personident = personident,
            )
        } catch (e: Exception) {
            log.error("Failed to preload cache callId=$callId", e)
        }
    }

    suspend fun preloadCacheForPersonAccess(callId: String, personidenter: List<String>) {
        personidenter.map { Personident(it) }.forEach { personident ->
            preloadPersonInfoCache(
                callId = callId,
                personident = personident,
            )
        }
    }

    private fun auditLog(tilganger: Map<Personident, Tilgang>, veilederIdent: String, appName: String) {
        tilganger.forEach { (personident, tilgang) ->
            auditLog(
                CEF(
                    suid = veilederIdent,
                    duid = personident.value,
                    event = AuditLogEvent.Access,
                    permit = tilgang.erGodkjent,
                    appName = appName,
                )
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TilgangService::class.java)
        private val CHECK_PERSON_TILGANG_DISPATCHER = Dispatchers.IO.limitedParallelism(20)

        const val TILGANG_TIL_TJENESTEN_PREFIX = "tilgang-til-tjenesten-"
        const val TILGANG_TIL_ENHET_PREFIX = "tilgang-til-enhet-"
        const val TILGANG_TIL_PERSON_PREFIX = "tilgang-til-person-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L

        const val TILGANGSMASKIN_BASE = "${METRICS_NS}_tilgangsmaskin"
        const val TILGANGSMASKIN_OK = "${TILGANGSMASKIN_BASE}_ok"
        const val TILGANGSMASKIN_DIFF = "${TILGANGSMASKIN_BASE}_diff"

        val COUNT_TILGANGSMASKIN_OK: Counter = Counter.builder(TILGANGSMASKIN_OK)
            .description("Counts the number of successful calls to tilgangsmaskin where access matches")
            .register(METRICS_REGISTRY)
        val COUNT_TILGANGSMASKIN_DIFF: Counter = Counter.builder(TILGANGSMASKIN_DIFF)
            .description("Counts the number of successful calls to tilgangsmaskin where access does not match")
            .register(METRICS_REGISTRY)
    }
}
