package no.nav.hjelpemidler.brille.pdl.service

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.exceptions.PersonNotFoundInPdl
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.brille.pdl.model.PersonDetaljerDto
import no.nav.hjelpemidler.brille.pdl.model.PersonDto
import no.nav.hjelpemidler.brille.pdl.model.toFormidlerDto
import no.nav.hjelpemidler.brille.pdl.model.toPersonDto

private val LOG = KotlinLogging.logger {}

class PdlService(
    private val pdlClient: PdlClient,
) {
    suspend fun hentAktorId(fnummer: String): String {
        try {
            val pdlResponse = pdlClient.hentIdentInfo(fnummer)
            if (pdlResponse.data?.hentIdenter?.identer?.get(0)?.ident == null) {
                LOG.warn("Fant ikke person i PDL")
                throw PersonNotFoundInPdl("Fant ikke person i PDL")
            }

            return pdlResponse.data.hentIdenter.identer[0].ident
        } catch (e: Exception) {
            LOG.error("Klarte ikke å hente aktørId fra PDL")
            throw e
        }
    }

    suspend fun hentPerson(fnummer: String): PersonDto {
        try {
            val pdlResponse = pdlClient.hentPersonInfo(fnummer)
            validerPdlOppslag(pdlResponse)
            return pdlResponse.toFormidlerDto()
        } catch (e: Exception) {
            LOG.warn("Klarte ikke å hente person fra PDL", e)
            throw e
        }
    }

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

    suspend fun validerPerson(fnummer: String) {
        val pdlResponse = pdlClient.hentPersonInfo(fnummer)
        validerPdlOppslag(pdlResponse)
    }
}
