package no.nav.hjelpemidler.brille.pdl

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.configuration.Environment
import no.nav.hjelpemidler.serialization.jackson.jsonMapper

private val log = KotlinLogging.logger {}

class PdlService(private val pdlClient: PdlClient) {
    suspend fun hentPerson(fnr: String): Person? {
        try {
            val pdlOppslag = pdlClient.hentPerson(fnr)
            if (pdlOppslag.harAdressebeskyttelse()) {
                throw PdlHarAdressebeskyttelseException()
            }
            if (Environment.current.isDev) {
                log.info {
                    "DEBUG: PDL raw result: ${jsonMapper.writeValueAsString(pdlOppslag)}"
                }
            }
            return pdlOppslag.data
        } catch (e: Exception) {
            log.error(e) {
                "Klarte ikke å hente person fra PDL"
            }
            throw e
        }
    }

    suspend fun helseSjekk() = pdlClient.helseSjekk()
}
