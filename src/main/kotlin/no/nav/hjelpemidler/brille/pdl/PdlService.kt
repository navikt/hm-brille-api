package no.nav.hjelpemidler.brille.pdl

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.jsonMapper

private val log = KotlinLogging.logger {}

class PdlService(
    private val pdlClient: PdlClient,
) {
    suspend fun hentPersonDetaljer(fnummer: String): PersonDetaljerDto {
        try {
            val pdlResponse = pdlClient.hentPersonDetaljer(fnummer)
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
            log.warn(e) {
                "Klarte ikke å hente person fra PDL"
            }
            throw e
        }
    }
}
