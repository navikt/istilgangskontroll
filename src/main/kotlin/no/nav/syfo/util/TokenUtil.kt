package no.nav.syfo.util

import com.auth0.jwt.JWT

const val JWT_CLAIM_NAVIDENT = "NAVident"

fun getNAVIdentFromToken(token: String): String {
    val decodedJWT = JWT.decode(token)
    return decodedJWT.claims[JWT_CLAIM_NAVIDENT]!!.asString()
}
