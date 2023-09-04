package no.nav.syfo.client.behandlendeenhet

import java.io.Serializable

data class BehandlendeEnhetDTO(
    var enhetId: String,
    var navn: String
) : Serializable
