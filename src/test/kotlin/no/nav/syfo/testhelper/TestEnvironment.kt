package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.cache.RedisEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
import no.nav.syfo.client.azuread.PreAuthorizedApp

fun testEnvironment() = Environment(
    azure = AzureEnvironment(
        appClientId = "istilgangskontroll-client-id",
        appClientSecret = "istilgangskontroll-secret",
        preAuthorizedApps = testAzureAppPreAuthorizedApps,
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),

    redis = RedisEnvironment(
        host = "localhost",
        port = 6599,
        secret = "password",
    ),

    kode6Id = "kode6Id",
    kode7Id = "kode7Id",
    syfoId = "syfoId",
    skjermingId = "skjermingId",
    nasjonalId = "nasjonalId",
    regionalId = "regionalId",
    papirsykmeldingId = "papirsykmeldingId",

    clients = ClientsEnvironment(
        graphApiUrl = "graphApiClientUrl",
        axsys = ClientEnvironment(
            baseUrl = "axsysBaseurl",
            clientId = "axsysClientId"
        ),
        skjermedePersoner = ClientEnvironment(
            baseUrl = "skjermedePersonerBaseurl",
            clientId = "skjermedePersonerClientId",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlBaseurl",
            clientId = "pdlClientId",
        ),
        behandlendeEnhet = ClientEnvironment(
            baseUrl = "behandlendeEnhetBaseurl",
            clientId = "behandlendeEnhetClientId",
        ),
        norgUrl = "norgBaseurl",
    ),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)

private const val syfooversiktsrvApplicationName: String = "syfooversiktsrv"
private const val syfomodiapersonApplicationName: String = "syfomodiaperson"
const val syfooversiktsrvClientId = "$syfooversiktsrvApplicationName-client-id"
const val syfomodiapersonClientId = "$syfomodiapersonApplicationName-client-id"

val testAzureAppPreAuthorizedApps = listOf(
    PreAuthorizedApp(
        name = "cluster:teamsykefravr:$syfooversiktsrvApplicationName",
        clientId = syfooversiktsrvClientId,
    ),
    PreAuthorizedApp(
        name = "cluster:teamsykefravr:$syfomodiapersonApplicationName",
        clientId = syfomodiapersonClientId,
    ),
)
