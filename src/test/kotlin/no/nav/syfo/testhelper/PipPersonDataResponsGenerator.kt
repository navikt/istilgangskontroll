package no.nav.syfo.testhelper

import no.nav.syfo.client.pdl.*

fun getUgradertInnbygger() = getInnbygger(Gradering.UGRADERT)

fun getInnbyggerWithKode7() = getInnbygger(Gradering.FORTROLIG)

fun getinnbyggerWithKode6() = getInnbygger(Gradering.STRENGT_FORTROLIG)

fun getInnbygger(gradering: Gradering) = PipPersondataResponse(
    person = PipPerson(
        adressebeskyttelse = listOf(
            PipAdressebeskyttelse(
                gradering = gradering,
            )
        ),
        doedsfall = emptyList(),
    ),
    geografiskTilknytning = null,
    identer = PipIdenter(emptyList()),
)
