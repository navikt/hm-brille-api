package no.nav.hjelpemidler.brille.pdl.service

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.brille.pdl.model.PersonDetaljerDto
import no.nav.hjelpemidler.brille.pdl.model.toPersonDto

private val LOG = KotlinLogging.logger {}

class PdlService(
    private val pdlClient: PdlClient,
) {
    suspend fun hentPersonDetaljer(fnummer: String): PersonDetaljerDto {
        try {
            val pdlResponse = pdlClient.hentPersonDetaljer(fnummer)
            validerPdlOppslag(pdlResponse)
            return pdlResponse.toPersonDto(fnummer) {
                "UKJENT"
            }
        } catch (e: Exception) {
            LOG.warn("Klarte ikke å hente person fra PDL", e)
            throw e
        }
    }
}
