package no.nav.syfo.domain

import no.nav.syfo.application.api.auth.Token
import no.nav.syfo.client.graphapi.Gruppe
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generateJWT
import no.nav.syfo.tilgang.AdRoller
import no.nav.syfo.tilgang.Enhet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VeilederTest {
    private val externalMockEnvironment = ExternalMockEnvironment()
    private val adRoller = AdRoller(externalMockEnvironment.environment)

    private val validToken = Token(
        generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
    )

    @Test
    fun `hasAccessToRole returns true when role ID is in adGrupper`() {
        val rolle = adRoller.SYFO
        val grupper = listOf(Gruppe(uuid = rolle.id, adGruppenavn = "0000-GA-SYFO-SENSITIV"))
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertTrue(veileder.hasAccessToRole(rolle))
    }

    @Test
    fun `hasAccessToRole returns false when role ID is not in adGrupper`() {
        val rolle = adRoller.KODE6
        val grupper = listOf(Gruppe(uuid = adRoller.SYFO.id, adGruppenavn = "0000-GA-SYFO-SENSITIV"))
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertFalse(veileder.hasAccessToRole(rolle))
    }

    @Test
    fun `hasAccessToEnhet returns true when enhet is in veileders enheter`() {
        val enhetNr = "1234"
        val grupper = listOf(Gruppe(uuid = "123", adGruppenavn = "0000-GA-ENHET_$enhetNr"))
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertTrue(veileder.hasAccessToEnhet(Enhet(enhetNr)))
    }

    @Test
    fun `hasAccessToEnhet returns false when enhet is not in veileders enheter`() {
        val grupper = listOf(Gruppe(uuid = "123", adGruppenavn = "0000-GA-ENHET_1234"))
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertFalse(veileder.hasAccessToEnhet(Enhet("9999")))
    }

    @Test
    fun `enheter property correctly extracts enhet numbers from gruppe names`() {
        val grupper = listOf(
            Gruppe(uuid = "123", adGruppenavn = "0000-GA-ENHET_1234"),
            Gruppe(uuid = "123", adGruppenavn = "0000-GA-ENHET_5678"),
            Gruppe(uuid = adRoller.SYFO.id, adGruppenavn = "0000-GA-SYFO-SENSITIV"),
        )
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertEquals(2, veileder.enheter.size)
        assertTrue(veileder.enheter.contains(Enhet("1234")))
        assertTrue(veileder.enheter.contains(Enhet("5678")))
    }

    @Test
    fun `enheter property returns empty list when no valid enhet groups`() {
        val grupper = listOf(Gruppe(uuid = adRoller.SYFO.id, adGruppenavn = "0000-GA-SYFO-SENSITIV"))
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertTrue(veileder.enheter.isEmpty())
    }

    @Test
    fun `veileder can have multiple roles`() {
        val grupper = listOf(
            Gruppe(uuid = adRoller.SYFO.id, adGruppenavn = "0000-GA-SYFO-SENSITIV"),
            Gruppe(uuid = adRoller.NASJONAL.id, adGruppenavn = "0000-GA-GOSYS_NASJONAL"),
            Gruppe(uuid = adRoller.KODE6.id, adGruppenavn = "0000-GA-Strengt_Fortrolig_Adresse"),
        )
        val veileder = Veileder(
            veilederident = UserConstants.VEILEDER_IDENT,
            token = validToken,
            adGrupper = grupper
        )

        assertTrue(veileder.hasAccessToRole(adRoller.SYFO))
        assertTrue(veileder.hasAccessToRole(adRoller.NASJONAL))
        assertTrue(veileder.hasAccessToRole(adRoller.KODE6))
        assertFalse(veileder.hasAccessToRole(adRoller.KODE7))
    }
}
