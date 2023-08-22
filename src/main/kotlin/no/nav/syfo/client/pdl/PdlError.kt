package no.nav.syfo.client.pdl

import java.io.Serializable

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension,
) : Serializable

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?,
) : Serializable

data class PdlErrorExtension(
    val code: String?,
    val classification: String,
) : Serializable

fun PdlError.errorMessage(): String {
    return "${this.message} with code: ${extensions.code} and classification: ${extensions.classification}"
}
