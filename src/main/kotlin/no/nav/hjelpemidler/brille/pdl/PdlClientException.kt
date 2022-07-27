package no.nav.hjelpemidler.brille.pdl

import com.expediagroup.graphql.client.types.GraphQLClientError
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.writePrettyString

open class PdlClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    constructor(errors: List<GraphQLClientError>) : this(jsonMapper.writePrettyString(errors))
}

class PdlNotFoundException : PdlClientException("code: $KODE") {
    companion object {
        const val KODE = "not_found"
    }
}

class PdlBadRequestException : PdlClientException("code: $KODE") {
    companion object {
        const val KODE = "bad_request"
    }
}

class PdlUnauthorizedException : PdlClientException("code: $KODE") {
    companion object {
        const val KODE = "unauthenticated"
    }
}

class PdlHarAdressebeskyttelseException : PdlClientException("Person har adressebeskyttelse")
