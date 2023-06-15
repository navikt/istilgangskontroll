package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.client.azuread.AzureEnvironment

fun testEnvironment() = Environment(
    azure = AzureEnvironment(
        appClientId = "istilgangskontroll-client-id",
        appClientSecret = "istilgangskontroll-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),

    oldKode6Id = "oldKode6Id",
    kode6Id = "kode6Id",
    oldKode7Id = "oldKode7Id",
    kode7Id = "kode7Id",
    syfoId = "syfoId",
    oldSkjermingId = "oldSkjermingId",
    skjermingId = "skjermingId",
    nasjonalId = "nasjonalId",
    utvidbarNasjonalId = "utvidbarNasjonalId",
    regionalId = "regionalId",
    utvidbarRegionalId = "utvidbarRegionalId",
    papirsykmeldingId = "papirsykmeldingId",

    clients = ClientsEnvironment(
        graphApiUrl = "graphApiClientUrl",
    ),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
