package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.cache.RedisConfig
import no.nav.syfo.client.azuread.AzureEnvironment
import no.nav.syfo.client.azuread.PreAuthorizedApp
import java.net.URI

fun testEnvironment() = Environment(
    azure = AzureEnvironment(
        appClientId = "istilgangskontroll-client-id",
        appClientSecret = "istilgangskontroll-secret",
        preAuthorizedApps = testAzureAppPreAuthorizedApps,
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),

    redisConfig = RedisConfig(
        redisUri = URI("http://localhost:6379"),
        redisDB = 0,
        redisUsername = "redisUser",
        redisPassword = "redisPassword",
        ssl = false,
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
