package no.nav.hjelpemidler.brille.avtale

open class AvtaleException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class AvtaleManglerTilgangException(orgnr: String) : AvtaleException("Mangler tilgang til orgnr: $orgnr")
