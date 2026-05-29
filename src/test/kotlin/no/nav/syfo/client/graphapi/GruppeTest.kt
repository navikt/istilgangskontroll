package no.nav.syfo.client.graphapi

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class GruppeTest {

    @ParameterizedTest(name = "Gruppe med displayName: `{0}` skal returnere enhetNr: `{1}`")
    @CsvSource(
        value = [
            "0000-GA-ENHET_0123, 0123",
            "0000-GA-ENHET_0100, 0100",
            "0000-GA-ENHET_0456, 0456",
        ]
    )
    fun `DisplayName med riktig format for enhet`(displayName: String, expectedEnhetNr: String) {
        val gruppe = Gruppe(uuid = "uuid", adGruppenavn = displayName)
        assertEquals(expectedEnhetNr, gruppe.getEnhetNr())
    }

    @ParameterizedTest(name = "Gruppe med displayName: `{0}` skal returnere null for enhetNr")
    @NullAndEmptySource
    @ValueSource(
        strings = [
            "0000-GA-Egne_ansatte",
            "0000-GA-GEO_NASJONAL",
            "0000-GA-GEO_0123",
            "PREFIX_0000-GA-ENHET_0123",
            "0000-GA-ENHET_0123_POSTFIX",
            "0000-GA-ENHET_ABCD"
        ]
    )
    fun `DisplayName som ikke inneholder enhetNr`(displayName: String?) {
        val gruppe = Gruppe(uuid = "uuid", adGruppenavn = displayName)
        assertNull(gruppe.getEnhetNr())
    }

    @ParameterizedTest(name = "Gruppe med displayName: `{0}` skal returnere geoKode: `{1}`")
    @CsvSource(
        value = [
            "0000-GA-GEO_0123, 0123",
            "0000-GA-GEO_0301, 0301",
            "0000-GA-GEO_1234, 1234",
            "0000-GA-GEO_030101, 030101",
            "0000-GA-GEO_010203, 010203",
        ]
    )
    fun `DisplayName med riktig format for GEO-gruppe`(displayName: String, expectedGeoKode: String) {
        val gruppe = Gruppe(uuid = "uuid", adGruppenavn = displayName)
        assertEquals(expectedGeoKode, gruppe.getGeoKode())
    }

    @ParameterizedTest(name = "Gruppe med displayName: `{0}` skal returnere null for geoKode")
    @NullAndEmptySource
    @ValueSource(
        strings = [
            "0000-GA-GEO_NASJONAL",
            "0000-GA-GOSYS_NASJONAL",
            "0000-GA-ENHET_0123",
            "PREFIX_0000-GA-GEO_0123",
            "0000-GA-GEO_0123_POSTFIX",
            "0000-GA-GEO_ABCD",
            "0000-GA-GEO_01234",
            "0000-GA-GEO_0123456",
        ]
    )
    fun `DisplayName som ikke inneholder geoKode`(displayName: String?) {
        val gruppe = Gruppe(uuid = "uuid", adGruppenavn = displayName)
        assertNull(gruppe.getGeoKode())
    }
}
