package no.nav.syfo.client.graphapi

import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class GraphApiUserGroupsResponseTest {

    @ParameterizedTest(name = "GraphApiGroup med displayName: `{0}` skal returnere enhetNr: `{1}`")
    @CsvSource(
        value = [
            "0000-GA-ENHET_0123, 0123",
            "0000-GA-ENHET_0100, 0100",
            "0000-GA-ENHET_0456, 0456",
        ]
    )
    fun `DisplayName med riktig format for enhet`(displayName: String, expectedEnhetNr: String) {
        val group = GraphApiGroup(id = "uuid", displayName = displayName, mailNickname = null)
        assertEquals(expectedEnhetNr, group.getEnhetNr())
    }

    @ParameterizedTest(name = "GraphApiGroup med displayName: `{0}` skal returnere null")
    @NullAndEmptySource
    @ValueSource(
        strings = [
            "0000-GA-Egne_ansatte",
            "0000-GA-GOSYS_NASJONAL",
        ]
    )
    fun `DisplayName som ikke inneholder enhetNr`(displayName: String?) {
        val group = GraphApiGroup(id = "uuid", displayName = displayName, mailNickname = null)
        assertNull(group.getEnhetNr())
    }
}
