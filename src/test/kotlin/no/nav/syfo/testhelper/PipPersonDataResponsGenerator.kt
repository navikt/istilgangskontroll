package no.nav.syfo.testhelper

import no.nav.syfo.client.pdl.*

fun getUgradertInnbygger() = getInnbygger(Gradering.UGRADERT)

fun getInnbyggerWithKode7() = getInnbygger(Gradering.FORTROLIG)

fun getinnbyggerWithKode6() = getInnbygger(Gradering.STRENGT_FORTROLIG)

fun getUgradertInnbyggerWithUtlandGT() = getInnbygger(Gradering.UGRADERT, true)

fun getInnbygger(gradering: Gradering, isUtland: Boolean = false) = PipPersondataResponse(
    person = PipPerson(
        adressebeskyttelse = listOf(
            PipAdressebeskyttelse(
                gradering = gradering,
            )
        ),
        doedsfall = emptyList(),
    ),
    geografiskTilknytning = if (isUtland) getPipGTUtland() else getPipGTBydel(),
    identer = PipIdenter(emptyList()),
)

fun getPipGTBydel() = PipGeografiskTilknytning(
    gtType = GeografiskTilknytningType.BYDEL.name,
    gtBydel = UserConstants.ENHET_VEILEDER_GT,
    gtKommune = null,
    gtLand = null,
)

fun getPipGTUtland() = PipGeografiskTilknytning(
    gtType = GeografiskTilknytningType.UTLAND.name,
    gtBydel = null,
    gtKommune = null,
    gtLand = "sverige",
)
