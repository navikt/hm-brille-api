package no.nav.hjelpemidler.brille.pdl.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.pdl.client.PdlClient
import no.nav.hjelpemidler.brille.pdl.model.PersonDetaljerDto
import no.nav.hjelpemidler.brille.pdl.model.toPersonDto

private val LOG = KotlinLogging.logger {}

private val objectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

class PdlService(
    private val pdlClient: PdlClient,
) {
    suspend fun hentPersonDetaljer(fnummer: String): PersonDetaljerDto {
        try {
            val pdlResponse = pdlClient.hentPersonDetaljer(fnummer)
            validerPdlOppslag(pdlResponse)
            if (Configuration.profile == Profile.DEV) LOG.info(
                "DEBUG: PDL raw result: ${
                    objectMapper.writeValueAsString(
                        pdlResponse
                    )
                }"
            )
            return pdlResponse.toPersonDto(fnummer) {
                "UKJENT"
            }
        } catch (e: Exception) {
            LOG.warn("Klarte ikke å hente person fra PDL", e)
            throw e
        }
    }
}
