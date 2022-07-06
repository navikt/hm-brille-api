package no.nav.hjelpemidler.brille.enhetsregisteret

class EnhetsregisteretClientException(message: String, cause: Throwable?) : RuntimeException(message, cause)

class EnhetsregisteretServiceException(message: String) : RuntimeException(message)
