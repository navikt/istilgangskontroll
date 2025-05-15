package no.nav.syfo.client.behandlendeenhet

import java.io.Serializable
import java.time.LocalDateTime

data class BehandlendeEnhetDTO(
    val geografiskEnhet: EnhetDTO,
    val oppfolgingsenhetDTO: OppfolgingsenhetDTO?,
) : Serializable

data class OppfolgingsenhetDTO(
    val enhet: EnhetDTO,
    val createdAt: LocalDateTime,
    val veilederident: String,
) : Serializable

data class EnhetDTO(
    val enhetId: String,
    val navn: String,
) : Serializable
