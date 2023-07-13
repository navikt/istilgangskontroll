package no.nav.syfo.application.api.auth

import com.auth0.jwt.JWT

const val JWT_CLAIM_NAVIDENT = "NAVident"

data class Token(val value: String)

fun Token.getNAVIdent(): String {
    val decodedJWT = JWT.decode(this.value)
    return decodedJWT.claims[JWT_CLAIM_NAVIDENT]!!.asString()
}

fun Token.containsNAVIdent(): Boolean {
    val decodedJWT = JWT.decode(this.value)
    return decodedJWT.claims[JWT_CLAIM_NAVIDENT] != null
}

fun Token.isMissingNAVIdent() = !containsNAVIdent()
