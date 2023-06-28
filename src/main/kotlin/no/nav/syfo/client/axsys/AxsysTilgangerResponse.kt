package no.nav.syfo.client.axsys

data class AxsysTilgangerResponse(
    val enheter: List<AxsysEnhet>,
)

data class AxsysEnhet(
    val enhetId: String,
    val navn: String,
)
