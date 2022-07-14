package no.nav.hjelpemidler.brille.pdl

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.HentPersonExtensions.toPersonDto

private val log = KotlinLogging.logger {}

class PdlService(private val pdlClient: PdlClient) {
    suspend fun hentPerson(fnr: String): PersonDto? {
        try {
            val pdlOppslag = pdlClient.hentPerson(fnr)
            if (Configuration.dev) {
                log.info {
                    "DEBUG: PDL raw result: ${jsonMapper.writeValueAsString(pdlOppslag)}"
                }
            }
            return pdlOppslag.data?.toPersonDto(fnr)
        } catch (e: Exception) {
            log.error(e) {
                "Klarte ikke å hente person fra PDL"
            }
            throw e
        }
    }
}
