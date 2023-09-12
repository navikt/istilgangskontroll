package no.nav.syfo.client.norg.domain

data class NorgEnhet(
    var aktiveringsdato: String?,
    var antallRessurser: String?,
    var enhetNr: String,
    var kanalstrategi: String?,
    var navn: String,
    var nedleggelsesdato: String?,
    var oppgavebehandler: String?,
    var orgNivaa: String?,
    var orgNrTilKommunaltNavKontor: String?,
    var organisasjonsnummer: String?,
    var sosialeTjenester: String?,
    var status: String,
    var type: String?,
    var underAvviklingDato: String?,
    var underEtableringDato: String?,
    var versjon: String?,
)

enum class Enhetsstatus(val formattedName: String) {
    UNDER_ETABLERING("Under etablering"),
    AKTIV("Aktiv"),
    UNDER_AVVIKLING("Under avvikling"),
    NEDLAGT("Nedlagt");
}
