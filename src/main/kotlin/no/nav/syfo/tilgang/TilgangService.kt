package no.nav.syfo.tilgang

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.cache.ValkeyStore
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
import no.nav.syfo.domain.Veileder
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
    suspend fun getVeileder(
        token: Token,
        callId: String,
    ): Veileder {
        val veilederident = token.getNAVIdent()
        val veiledergrupper = graphApiClient.getGrupperForVeilederOgCache(
            token = token,
            callId = callId,
        )
        return Veileder(
            veilederident = veilederident,
            token = token,
            adGrupper = veiledergrupper,
        )
    }

    fun checkTilgangToSyfo(veileder: Veileder): Tilgang {
        // Hvis tilgangen for veileder var cachet, så blir det egentlig et unødvendig kall til graph-api-cachen når vi nå tvinger inn veileder
        val veilederident = veileder.veilederident
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederident"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        return if (cachedTilgang != null) {
            cachedTilgang
        } else {
            Tilgang(
                erGodkjent = veileder.hasAccessToRole(adRoller.SYFO)
            ).also { tilgang ->
                if (tilgang.erGodkjent) {
                    valkeyStore.setObject(
                        key = cacheKey,
                        value = tilgang,
                        expireSeconds = TWELVE_HOURS_IN_SECS
                    )
                }
            }
        }
    }

    fun checkTilgangToEnhet(veileder: Veileder, enhet: Enhet): Tilgang {
        val veilederident = veileder.veilederident
        val cacheKey = "$TILGANG_TIL_ENHET_PREFIX$veilederident-$enhet"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        return if (cachedTilgang != null) {
            cachedTilgang
        } else {
            Tilgang(erGodkjent = veileder.hasAccessToEnhet(enhet)).also { tilgang ->
                if (tilgang.erGodkjent) {
                    valkeyStore.setObject(
                        key = cacheKey,
                        value = tilgang,
                        expireSeconds = TWELVE_HOURS_IN_SECS
                    )
                }
            }
        }
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
        veileder: Veileder,
    ): Boolean {
        if (veileder.hasAccessToRole(adRoller.NASJONAL)) {
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
                token = veileder.token,
            )
        } catch (exc: Exception) {
            log.warn("Didn't get enhet for innbygger, unable to check geografisk access callId=$callId", exc)
            return false
        }

        val behandlendeEnhet = Enhet(innbyggersEnhetNr)

        if (veileder.hasAccessToEnhet(behandlendeEnhet)) {
            return true
        }

        if (veileder.hasAccessToRole(adRoller.REGIONAL)) {
            val veiledersEnheterOgOverordnedeEnheter =
                veiledersEnheterOgOverordnedeEnheter(enheter = veileder.enheter, callId = callId)
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

    private suspend fun isAdressebeskyttelseAccessGodkjent(
        callId: String,
        personident: Personident,
        veileder: Veileder,
    ): Boolean {
        val person = pdlClient.getPerson(
            callId = callId,
            personident = personident,
        )

        return if (person.isKode6() && !veileder.hasAccessToRole(adRoller.KODE6)) {
            false
        } else if (person.isKode7() && !veileder.hasAccessToRole(adRoller.KODE7)) {
            false
        } else {
            true
        }
    }

    private suspend fun isSkjermetAccessGodkjent(
        callId: String,
        personident: Personident,
        veileder: Veileder,
    ): Boolean {
        val personIsSkjermet = skjermedePersonerPipClient.getIsSkjermetWithOboToken(
            callId = callId,
            personident = personident,
            token = veileder.token,
        )

        return if (!personIsSkjermet) {
            true
        } else {
            veileder.hasAccessToRole(adRoller.EGEN_ANSATT)
        }
    }

    suspend fun checkTilgangToPersonWithPapirsykmelding(
        personident: Personident,
        veileder: Veileder,
        callId: String,
        appName: String,
    ): Tilgang {
        return if (veileder.hasAccessToRole(adRoller.PAPIRSYKMELDING)) {
            checkTilgangToPerson(
                personident = personident,
                veileder = veileder,
                callId = callId,
                appName = appName,
            )
        } else {
            Tilgang(erGodkjent = false)
        }
    }

    suspend fun checkTilgangToPerson(
        personident: Personident,
        veileder: Veileder,
        callId: String,
        appName: String,
    ): Tilgang {
        val veilederident = veileder.veilederident
        val cacheKey = "$TILGANG_TIL_PERSON_PREFIX$veilederident-$personident"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        val tilgang = if (cachedTilgang != null) {
            cachedTilgang
        } else {
            checkTilgangToPersonAndCache(
                personident = personident,
                veileder = veileder,
                cacheKey = cacheKey,
                callId = callId,
            )
        }
        if (cachedTilgang == null) {
            supervisorScope {
                launch {
                    val tilgangsmaskinTilgang = tilgangsmaskin.hasTilgang(veileder.token, personident, callId)
                    if (!tilgangsmaskinTilgang.hasAccess && tilgang.erGodkjent) {
                        COUNT_TILGANGSMASKIN_DIFF.increment()
                        log.info("Tilgangsmaskin gir annet resultat (ikke ok: ${tilgangsmaskinTilgang.problemDetailResponse?.begrunnelse}) for ${veileder.veilederident} enn istilgangskontroll (ok): $callId")
                    } else if (tilgangsmaskinTilgang.hasAccess && !tilgang.erGodkjent) {
                        COUNT_TILGANGSMASKIN_DIFF.increment()
                        log.info("Tilgangsmaskin gir annet resultat (ok) for ${veileder.veilederident} enn istilgangskontroll (ikke ok): $callId")
                    } else {
                        COUNT_TILGANGSMASKIN_OK.increment()
                    }
                }
            }
        }
        auditLog(
            CEF(
                suid = veilederident,
                duid = personident.value,
                event = AuditLogEvent.Access,
                permit = tilgang.erGodkjent,
                appName = appName,
            )
        )
        return tilgang
    }

    suspend fun checkTilgangToPersons(
        personidenter: List<Personident>,
        veileder: Veileder,
        callId: String,
    ): Map<Personident, Tilgang> {
        val veilederIdent = veileder.veilederident
        val cacheKeysToPersonident: Map<String, Personident> = personidenter.associateBy { personident ->
            "$TILGANG_TIL_PERSON_PREFIX$veilederIdent-$personident"
        }

        val cacheKeysToValue: Map<String, Tilgang?> =
            valkeyStore.getObjects(keys = cacheKeysToPersonident.keys.toList())

        val (cachedEntries, missingEntries) = cacheKeysToValue.entries.partition { it.value != null }

        val cachedTilganger: Map<Personident, Tilgang> =
            cachedEntries.associate { entry ->
                val personident = cacheKeysToPersonident[entry.key]!!
                personident to entry.value!!
            }

        val hentetTilganger = supervisorScope {
            missingEntries.map { missingEntry ->
                async(CHECK_PERSON_TILGANG_DISPATCHER) {
                    val cacheKey = missingEntry.key
                    val personident = cacheKeysToPersonident[cacheKey]!!
                    val tilgang = checkTilgangToPersonAndCache(
                        personident = personident,
                        veileder = veileder,
                        cacheKey = cacheKey,
                        callId = callId,
                    )
                    personident to tilgang
                }
            }.awaitAll().toMap()
        }
        val godkjente = cachedTilganger + hentetTilganger
        return godkjente
    }

    private suspend fun checkTilgangToPersonAndCache(
        personident: Personident,
        veileder: Veileder,
        cacheKey: String,
        callId: String,
    ): Tilgang {
        val erGodkjent = if (
            !isGeografiskAccessGodkjent(
                callId = callId,
                personident = personident,
                veileder = veileder,
            )
        ) {
            false
        } else if (!isSkjermetAccessGodkjent(callId = callId, personident = personident, veileder = veileder)) {
            false
        } else if (!isAdressebeskyttelseAccessGodkjent(callId = callId, personident = personident, veileder = veileder)) {
            false
        } else {
            true
        }

        return Tilgang(erGodkjent = erGodkjent).also { tilgang ->
            if (tilgang.erGodkjent) {
                valkeyStore.setObject(
                    key = cacheKey,
                    value = tilgang,
                    expireSeconds = TWELVE_HOURS_IN_SECS
                )
            }
        }
    }

    suspend fun filterIdenterByVeilederAccess(
        callId: String,
        token: Token,
        personidenter: List<String>,
    ): List<String> {
        val veileder = getVeileder(token, callId)

        if (!veileder.hasAccessToRole(adRoller.SYFO)) {
            return emptyList()
        }
        preloadOboTokens(callId = callId, token = veileder.token)
        val validPersonidenter = personidenter.filterValidPersonidenter()

        val godkjente = checkTilgangToPersons(
            personidenter = validPersonidenter,
            veileder = veileder,
            callId = callId,
        )
            .filter { (_, tilgang) -> tilgang.erGodkjent }
            .map { (personident, _) -> personident.value }

        if (validPersonidenter.size < MAX_BULK_SIZE_TILGANGSMASKIN) {
            supervisorScope {
                launch {
                    val personidenterToCheck = validPersonidenter.map { it.value }
                    val tilgangsmaskinTilgang = tilgangsmaskin.hasTilgang(veileder.token, personidenterToCheck, callId)
                    val baseLineDenied = personidenterToCheck - godkjente
                    val tilgangsmaskinDenied = personidenterToCheck - tilgangsmaskinTilgang
                    val agreeDenied = baseLineDenied.intersect(tilgangsmaskinDenied)
                    val diffDeniedByBaseline = baseLineDenied - agreeDenied
                    val diffDeniedByTilgangsmaskin = tilgangsmaskinDenied - agreeDenied
                    if (diffDeniedByBaseline.isNotEmpty()) {
                        COUNT_TILGANGSMASKIN_DIFF.increment(diffDeniedByBaseline.size.toDouble())
                        log.info("Tilgangsmaskin gir annet resultat (ok for ${diffDeniedByBaseline.size} forekomster) for ${veileder.veilederident} enn istilgangskontroll (ikke ok): $callId")
                    }
                    if (diffDeniedByTilgangsmaskin.isNotEmpty()) {
                        COUNT_TILGANGSMASKIN_DIFF.increment(diffDeniedByTilgangsmaskin.size.toDouble())
                        log.info("Tilgangsmaskin gir annet resultat (ikke ok for ${diffDeniedByTilgangsmaskin.size} forekomster) for ${veileder.veilederident} enn istilgangskontroll (ok): $callId")
                    }
                    COUNT_TILGANGSMASKIN_OK.increment(tilgangsmaskinTilgang.size.toDouble())
                }
            }
        }
        return godkjente
    }

    private suspend fun preloadOboTokens(
        callId: String,
        token: Token,
    ) {
        azureAdClient.getOnBehalfOfToken(skjermedePersonerPipClient.clientId, token, callId)
        azureAdClient.getOnBehalfOfToken(behandlendeEnhetClient.clientId, token, callId)
        // pdlClient bruker system token, så trenger ingen OBO-token preloading
        // graphApiClient har implisitt cachet obo-token via getGrupperForVeilederOgCache-kallet
    }

    private suspend fun preloadPersonInfoCache(callId: String, personident: Personident) {
        try {
            skjermedePersonerPipClient.getIsSkjermetWithSystemToken(
                callId = callId,
                personident = personident,
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
