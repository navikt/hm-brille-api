package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.validerPdlOppslag
import org.slf4j.MDC
import java.util.UUID

private val log = KotlinLogging.logger {}

class MedlemskapBarn(
    private val medlemskapClient: MedlemskapClient,
    private val pdlClient: PdlClient,
) {
    fun sjekkMedlemskapBarn(fnrBarn: String): MedlemskapResultat = runBlocking {
        val baseCorrelationId = MDC.get(MDC_CORRELATION_ID)
        withLoggingContext(
            mapOf()
        ) {
            log.info("Sjekker medlemskap for barn")

            // Slå opp pdl informasjon om barnet
            val pdlBarn = pdlClient.hentPersonDetaljer(fnrBarn)
            validerPdlOppslag(pdlBarn)

            val vergemaalEllerFremtidsfullmakt = pdlBarn.data?.hentPerson?.vergemaalEllerFremtidsfullmakt
            val foreldreBarnRelasjon = pdlBarn.data?.hentPerson?.forelderBarnRelasjon

            log.debug("PDL response: ${jsonMapper.writeValueAsString(pdlBarn)}")
            log.debug("vergemaalEllerFremtidsfullmakt: ${jsonMapper.writeValueAsString(vergemaalEllerFremtidsfullmakt)}")
            log.debug("foreldreBarnRelasjon: ${jsonMapper.writeValueAsString(foreldreBarnRelasjon)}")

            // TODO: Avklar folkeregistrert adresse i Norge, ellers stopp behandling?

            for (vergeEllerForelder in listOf("abc", "def")) {
                val correlationIdMedlemskap = "$baseCorrelationId-${UUID.randomUUID()}"
                withLoggingContext(
                    mapOf(
                        "correlation-id-subcall-medlemskap" to correlationIdMedlemskap,
                    )
                ) {
                    log.info("Sjekker barns verge/forelder")
                    // TODO: Slå opp verge / foreldre i PDL for å sammenligne folkeregistrerte adresse
                    // TODO: Gitt adresse match: Sjekk medlemskap:
                    //   val medlemskap = medlemskapClient.slåOppMedlemskap(fnrVergeEllerForelder, innerCorrelationId)
                    // TODO: Gitt medlemskap: svar ok med en return@runBlocking her
                }
            }

            // Hvis man kommer sålangt så har man sjekket alle verger og foreldre, og ingen både bor på samme folk.reg.
            // adresse OG har et avklart medlemskap i folketrygden i følge LovMe-tjenesten.
            MedlemskapResultat(true, medlemskapBevist = false, uavklartMedlemskap = true, saksgrunnlag = listOf())
        }
    }
}

data class MedlemskapResultat(
    val kanSøke: Boolean, // Ja eller antatt pga. folkereg. addresse i norge
    val medlemskapBevist: Boolean,
    val uavklartMedlemskap: Boolean,
    val saksgrunnlag: List<Saksgrunnlag>,
)

data class Saksgrunnlag(
    val kilde: String,
    val saksgrunnlag: JsonNode,
)
