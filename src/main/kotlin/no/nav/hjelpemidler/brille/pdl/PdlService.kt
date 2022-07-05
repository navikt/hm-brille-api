package no.nav.hjelpemidler.brille.pdl

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.jsonMapper

private val log = KotlinLogging.logger {}

class PdlService(
    private val pdlClient: PdlClient,
) {
    suspend fun hentPerson(fnummer: String): PersonDetaljerDto {
        try {
            val pdlResponse = pdlClient.hentPerson(fnummer)
            validerPdlOppslag(pdlResponse)
            if (Configuration.profile == Profile.DEV) {
                log.info {
                    "DEBUG: PDL raw result: ${jsonMapper.writeValueAsString(pdlResponse)}"
                }
            }
            return pdlResponse.toPersonDto(fnummer) {
                "UKJENT"
            }
        } catch (e: Exception) {
            log.error(e) {
                "Klarte ikke å hente person fra PDL"
            }
            throw e
        }
    }
}
