package no.nav.syfo.tilgang

import io.micrometer.core.instrument.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.application.api.auth.getNAVIdent
import no.nav.syfo.cache.IValkeyStore
import no.nav.syfo.cache.getObject
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
    val valkeyStore: IValkeyStore,
    val tilgangsmaskin: TilgangsmaskinClient,
    val useTilgangsmaskin: Boolean,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
        val veilederident = veileder.veilederident
        val cacheKey = "$TILGANG_TIL_TJENESTEN_PREFIX$veilederident"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        return if (cachedTilgang != null) {
            cachedTilgang
        } else {
            Tilgang(
                erGodkjent = veileder.hasFullEllerLesTilgang(adRoller),
            ).utvidMedTilganger(
                veileder = veileder,
                adRoller = adRoller,
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

    fun checkTilgangToFinnfastlege(veileder: Veileder): Tilgang {
        val veilederident = veileder.veilederident
        val cacheKey = "$TILGANG_TIL_FINNFASTLEGE_PREFIX$veilederident"
        val cachedTilgang: Tilgang? = valkeyStore.getObject(key = cacheKey)

        return if (cachedTilgang != null) {
            cachedTilgang
        } else {
            Tilgang(
                erGodkjent = veileder.hasFinnfastlegeTilgang(adRoller),
            ).utvidMedTilganger(
                veileder = veileder,
                adRoller = adRoller,
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
            Tilgang(
                erGodkjent = veileder.hasAccessToEnhet(enhet),
            ).utvidMedTilganger(
                veileder = veileder,
                adRoller = adRoller,
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

        if (geografiskTilknytning?.value == null) {
            log.warn("Didn't get GT for innbygger, unable to check geografisk access callId=$callId")
            return false
        }

        if (geografiskTilknytning.isKommuneOrBydel() && veileder.hasAccessToGeo(geografiskTilknytning.value)) {
            return true
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
        return veileder.hasAccessToEnhet(behandlendeEnhet)
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
        if (tilgang.erGodkjent) {
            auditLog(
                CEF(
                    suid = veilederident,
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

        val missingPersonidentToCacheKey: List<Pair<Personident, String>> = missingEntries.map { missingEntry ->
            val cacheKey = missingEntry.key
            cacheKeysToPersonident[cacheKey]!! to cacheKey
        }

        val hentetTilganger = if (useTilgangsmaskin) {
            checkTilgangToPersonsBulkAndCache(
                personidentToCacheKey = missingPersonidentToCacheKey,
                veileder = veileder,
                callId = callId,
            )
        } else {
            supervisorScope {
                missingPersonidentToCacheKey.map { (personident, cacheKey) ->
                    async(CHECK_PERSON_TILGANG_DISPATCHER) {
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
        }
        return cachedTilganger + hentetTilganger
    }

    private suspend fun checkTilgangToPersonsBulkAndCache(
        personidentToCacheKey: List<Pair<Personident, String>>,
        veileder: Veileder,
        callId: String,
    ): Map<Personident, Tilgang> {
        return if (personidentToCacheKey.isEmpty()) {
            emptyMap()
        } else {
            supervisorScope {
                personidentToCacheKey.chunked(MAX_BULK_SIZE_TILGANGSMASKIN).map { chunk ->
                    async(CHECK_PERSON_TILGANG_DISPATCHER) {
                        val godkjentePersonidenter = tilgangsmaskin.hasTilgang(
                            veileder.token,
                            chunk.map { (personident, _) -> personident.value },
                            callId,
                        ).toSet()

                        chunk.map { (personident, cacheKey) ->
                            val tilgang = Tilgang(
                                erGodkjent = personident.value in godkjentePersonidenter,
                            ).utvidMedTilganger(
                                veileder = veileder,
                                adRoller = adRoller,
                            )
                            if (tilgang.erGodkjent) {
                                valkeyStore.setObject(
                                    key = cacheKey,
                                    value = tilgang,
                                    expireSeconds = TWELVE_HOURS_IN_SECS
                                )
                            }
                            personident to tilgang
                        }
                    }
                }.awaitAll().flatten().toMap()
            }
        }
    }

    private suspend fun checkTilgangToPersonAndCache(
        personident: Personident,
        veileder: Veileder,
        cacheKey: String,
        callId: String,
    ): Tilgang {
        val erGodkjent = if (useTilgangsmaskin) {
            tilgangsmaskin.hasTilgang(veileder.token, personident, callId).hasAccess
        } else {
            checkLegacyTilgangToPerson(
                personident = personident,
                veileder = veileder,
                callId = callId,
            )
        }
        return Tilgang(
            erGodkjent = erGodkjent,
        ).utvidMedTilganger(
            veileder = veileder,
            adRoller = adRoller,
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

    private suspend fun checkLegacyTilgangToPerson(
        personident: Personident,
        veileder: Veileder,
        callId: String,
    ) = if (
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

    suspend fun filterIdenterByVeilederAccess(
        callId: String,
        token: Token,
        personidenter: List<String>,
    ): List<String> {
        val veileder = getVeileder(token, callId)

        if (!veileder.hasFullEllerLesTilgang(adRoller)) {
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
        const val TILGANG_TIL_FINNFASTLEGE_PREFIX = "tilgang-til-finnfastlege-"
        const val TILGANG_TIL_ENHET_PREFIX = "tilgang-til-enhet-"
        const val TILGANG_TIL_PERSON_PREFIX = "tilgang-til-person-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
