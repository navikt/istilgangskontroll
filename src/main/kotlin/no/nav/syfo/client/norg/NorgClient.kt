package no.nav.syfo.client.norg

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.httpClientDefault
import no.nav.syfo.client.norg.domain.NorgEnhet
import no.nav.syfo.tilgang.Enhet
import no.nav.syfo.util.NAV_CALL_ID_HEADER
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory.getLogger

class NorgClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getOverordnetEnhetListForNAVKontor(
        callId: String,
        enhet: Enhet
    ): List<NorgEnhet> {
        val url = getOverordnetEnheterForNAVKontorUrl(enhet.id)
        try {
            val response: List<NorgEnhet> = httpClient.get(url) {
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }.body()

            if (response.isEmpty()) {
                log.error("No overordnede enheter returned from NORG2 for enhet $enhet, callId=$callId")
                COUNT_CALL_NORG_ENHET_FAIL.increment()
                throw RuntimeException("No overordnede enheter returned from NORG2 for enhet $enhet, callId=$callId")
            }

            COUNT_CALL_NORG_ENHET_SUCCESS.increment()
            return response
        } catch (e: ResponseException) {
            COUNT_CALL_NORG_ENHET_FAIL.increment()
            log.error(
                "Call to NORG2 for overordnet enhet failed with status HTTP-{} for enhet {}. {}",
                e.response.status,
                enhet.id,
                callIdArgument(callId)
            )
            throw e
        }
    }

    private fun getOverordnetEnheterForNAVKontorUrl(enhetNr: String): String {
        return "$baseUrl/norg2/api/v1/enhet/$enhetNr/overordnet?organiseringsType=$ORGANISERINGSTYPE"
    }

    companion object {
        private val log = getLogger(NorgClient::class.java)

        private const val ORGANISERINGSTYPE = "FYLKE"
    }
}
