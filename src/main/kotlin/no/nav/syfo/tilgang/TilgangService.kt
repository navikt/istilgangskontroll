package no.nav.syfo.tilgang

import no.nav.syfo.client.graphapi.GraphApiClient

class TilgangService(
    val graphApiClient: GraphApiClient,
    val adRoller: AdRoller,
) {

    suspend fun sjekkTilgangTilTjenesten(token: String): Tilgang {
        return Tilgang(
            harTilgang = graphApiClient.hasAccess(adRoller.SYFO, token)
        )
    }
}
