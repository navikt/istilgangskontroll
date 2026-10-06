package no.nav.syfo.tilgang

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
import no.nav.syfo.audit.AuditLogEvent
import no.nav.syfo.audit.CEF
import no.nav.syfo.audit.auditLog
import no.nav.syfo.client.graphapi.GraphApiClient
import no.nav.syfo.client.tilgangsmaskin.TilgangsmaskinClient
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Veileder
import no.nav.syfo.domain.filterValidPersonidenter
import kotlin.collections.component1
import kotlin.collections.component2

private const val MAX_BULK_SIZE_TILGANGSMASKIN = 1000

class TilgangService(
    val graphApiClient: GraphApiClient,
    val adRoller: AdRoller,
    val valkeyStore: IValkeyStore,
    val tilgangsmaskin: TilgangsmaskinClient,
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

        val hentetTilganger = checkTilgangToPersonsBulkAndCache(
            personidentToCacheKey = missingPersonidentToCacheKey,
            veileder = veileder,
            callId = callId,
        )
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

    suspend fun checkKjerneregelTilgangToPersonsBulk(
        personidenter: List<Personident>,
        veileder: Veileder,
        callId: String,
    ): Map<Personident, Tilgang> {
        return if (personidenter.isEmpty()) {
            emptyMap()
        } else {
            supervisorScope {
                personidenter.chunked(MAX_BULK_SIZE_TILGANGSMASKIN).map { chunk ->
                    async(CHECK_PERSON_TILGANG_DISPATCHER) {
                        val godkjentePersonidenter = tilgangsmaskin.hasKjerneTilgang(
                            veileder.token,
                            chunk.map { personident -> personident.value },
                            callId,
                        ).toSet()

                        chunk.map { personident ->
                            val tilgang = Tilgang(
                                erGodkjent = personident.value in godkjentePersonidenter,
                            ).utvidMedTilganger(
                                veileder = veileder,
                                adRoller = adRoller,
                            )
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
        val erGodkjent = tilgangsmaskin.hasTilgang(veileder.token, personident, callId).hasAccess
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

    suspend fun filterIdenterByVeilederAccess(
        callId: String,
        token: Token,
        personidenter: List<String>,
    ): List<String> {
        val veileder = getVeileder(token, callId)

        if (!veileder.hasFullEllerLesTilgang(adRoller)) {
            return emptyList()
        }
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

    suspend fun filterIdenterByVeilederKjernereglerAccess(
        callId: String,
        token: Token,
        personidenter: List<String>,
    ): List<String> {
        val veileder = getVeileder(token, callId)
        if (!veileder.hasFullEllerLesTilgang(adRoller)) {
            return emptyList()
        }
        val validPersonidenter = personidenter.filterValidPersonidenter()

        val godkjente = checkKjerneregelTilgangToPersonsBulk(
            personidenter = validPersonidenter,
            veileder = veileder,
            callId = callId,
        )
            .filter { (_, tilgang) -> tilgang.erGodkjent }
            .map { (personident, _) -> personident.value }

        return godkjente
    }

    companion object {
        private val CHECK_PERSON_TILGANG_DISPATCHER = Dispatchers.IO.limitedParallelism(20)

        const val TILGANG_TIL_TJENESTEN_PREFIX = "tilgang-til-tjenesten-"
        const val TILGANG_TIL_FINNFASTLEGE_PREFIX = "tilgang-til-finnfastlege-"
        const val TILGANG_TIL_ENHET_PREFIX = "tilgang-til-enhet-"
        const val TILGANG_TIL_PERSON_PREFIX = "tilgang-til-person-"
        const val TWELVE_HOURS_IN_SECS = 12 * 60 * 60L
    }
}
