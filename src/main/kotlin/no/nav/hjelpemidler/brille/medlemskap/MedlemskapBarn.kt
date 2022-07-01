package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.validerPdlOppslag

private val log = KotlinLogging.logger {}

class MedlemskapBarn(
    private val medlemskapClient: MedlemskapClient,
    private val pdlClient: PdlClient,
) {
    fun sjekkMedlemskapBarn(fnrBarn: String): MedlemskapResultat = runBlocking {
        // Slå opp pdl informasjon om barnet
        val pdlBarn = pdlClient.hentPersonDetaljer(fnrBarn)
        validerPdlOppslag(pdlBarn)

        val vergemaalEllerFremtidsfullmakt = pdlBarn.data?.hentPerson?.vergemaalEllerFremtidsfullmakt
        val foreldreBarnRelasjon = pdlBarn.data?.hentPerson?.forelderBarnRelasjon

        log.debug("PDL response: ${jsonMapper.writeValueAsString(pdlBarn)}")
        log.debug("vergemaalEllerFremtidsfullmakt: ${jsonMapper.writeValueAsString(vergemaalEllerFremtidsfullmakt)}")
        log.debug("foreldreBarnRelasjon: ${jsonMapper.writeValueAsString(foreldreBarnRelasjon)}")

        // TODO: Avklar folkeregistrert adresse i Norge, ellers stopp behandling?

        for (vergeEllerForelder in listOf("", "")) {
            // TODO: Slå opp verge / foreldre i PDL for å sammenligne folkeregistrerte adresse
            // TODO: Gitt adresse match: Sjekk medlemskap:
            //   val medlemskap = medlemskapClient.slåOppMedlemskap(fnrVergeEllerForelder)
            // TODO: Gitt medlemskap: svar ok med en return@runBlocking her
        }

        // Hvis man kommer sålangt så har man sjekket alle verger og foreldre, og ingen både bor på samme folk.reg.
        // adresse OG har et avklart medlemskap i folketrygden i følge LovMe-tjenesten.
        MedlemskapResultat(true, medlemskapBevist = false, uavklartMedlemskap = true, saksgrunnlag = listOf())
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
