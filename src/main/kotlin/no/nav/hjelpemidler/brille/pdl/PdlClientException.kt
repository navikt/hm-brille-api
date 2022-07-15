package no.nav.hjelpemidler.brille.pdl

open class PdlClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PdlPersonIkkeFunnetException : PdlClientException("Person ikke funnet")

class PdlPersonAdressebeskyttelseException : PdlClientException("Person har adressebeskyttelse")
