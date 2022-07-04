package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.ForelderBarnRelasjon
import no.nav.hjelpemidler.brille.pdl.ForelderBarnRelasjonRolle
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.pdl.VergemaalEllerFremtidsfullmakt
import no.nav.hjelpemidler.brille.pdl.validerPdlOppslag
import org.slf4j.MDC
import java.time.LocalDateTime
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
            val pdlBarn = pdlClient.medlemskapHentBarn(fnrBarn)
            validerPdlOppslag(pdlBarn)

            log.debug("PDL response: ${jsonMapper.writeValueAsString(pdlBarn)}")

            if (!sjekkFolkeregistrertAdresseINorge(pdlBarn)) {
                // Ingen av de folkeregistrerte bostedsadressene satt på barnet i PDL er en normal norsk adresse (kan
                // feks. fortsatt være utenlandskAdresse/ukjentBosted). Vi kan derfor ikke sjekke medlemskap i noe
                // register eller anta at man har medlemskap basert på at man har en norsk folkereg. adresse. Derfor
                // stopper vi opp behandling tidlig her!
                return@runBlocking MedlemskapResultat(false, false, false, listOf())
            }

            val vergerOgForeldre = prioriterVergerOgForeldreForSjekkMotMedlemskap(pdlBarn)
            log.debug("Prioritert liste for oppslag: $vergerOgForeldre")

            for ((rolle, fnrVergeEllerForelder) in vergerOgForeldre) {
                log.debug("Slår opp i PDL for $rolle: $fnrVergeEllerForelder")

                val correlationIdMedlemskap = "$baseCorrelationId+${UUID.randomUUID()}"
                withLoggingContext(
                    mapOf(
                        "correlation-id-subcall-medlemskap" to correlationIdMedlemskap,
                        "rolle" to rolle,
                    )
                ) {
                    log.info("Sjekker barns verge/forelder")

                    // Slå opp verge / foreldre i PDL for å sammenligne folkeregistrerte adresse
                    val pdlVergeEllerForelder = pdlClient.medlemskapHentVergeEllerForelder(fnrVergeEllerForelder)
                    log.debug("PDL response verge/forelder: ${jsonMapper.writeValueAsString(pdlVergeEllerForelder)}")

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

private fun sjekkFolkeregistrertAdresseINorge(pdlBarn: PdlPersonResponse): Boolean {
    // TODO: Avklar folkeregistrert adresse i Norge, ellers stopp behandling?
    // TODO: Hva med delt bostedsadresse (skilte foreldre), må kanskje ansees som en ekstra folkeregistrert adresse?
    val bostedsadresser = pdlBarn.data?.hentPerson?.bostedsadresse ?: listOf()
    return bostedsadresser.any { it.vegadresse != null || it.matrikkeladresse != null }
}

private fun prioriterVergerOgForeldreForSjekkMotMedlemskap(pdlBarn: PdlPersonResponse): List<Pair<String, String>> {
    // Lag en liste i prioritert rekkefølge for hvem vi skal slå opp i medlemskap-oppslag tjenesten. Her
    // prioriterer vi først verger (under antagelse om at foreldre kanskje har mistet forelderansvaret hvis
    // barnet har fått en annen verge). Etter det kommer foreldre relasjoner prioritert etter rolle.

    val vergemaalEllerFremtidsfullmakt = pdlBarn.data?.hentPerson?.vergemaalEllerFremtidsfullmakt ?: listOf()
    val foreldreBarnRelasjon = pdlBarn.data?.hentPerson?.forelderBarnRelasjon ?: listOf()

    log.debug("vergemaalEllerFremtidsfullmakt: ${jsonMapper.writeValueAsString(vergemaalEllerFremtidsfullmakt)}")
    log.debug("foreldreBarnRelasjon: ${jsonMapper.writeValueAsString(foreldreBarnRelasjon)}")

    val now = LocalDateTime.now()
    val vergerOgForeldre: List<Pair<String, String>> = listOf(
        vergemaalEllerFremtidsfullmakt.filter {
            // Sjekk om vi har et fnr for vergen ellers kan vi ikke slå personen opp i medlemskap-oppslag
            it.vergeEllerFullmektig.motpartsPersonident != null &&
                    // Bare se på vergerelasjoner som ikke har opphørt (feltet er null eller i fremtiden)
                    (it.folkeregistermetadata?.opphoerstidspunkt?.isAfter(now) ?: true) &&
                    (it.folkeregistermetadata?.gyldighetstidspunkt?.isBefore(now) ?: true)
        }.map {
            Pair("VERGE-${it.type ?: "ukjent-type"}", it.vergeEllerFullmektig.motpartsPersonident!!)
        },
        foreldreBarnRelasjon.filter {
            // Vi kan ikke slå opp medlemskap om forelder ikke har fnr
            it.relatertPersonsIdent != null &&
                    // Bare se på foreldrerelasjoner
                    it.minRolleForPerson == ForelderBarnRelasjonRolle.BARN &&
                    // Bare se på foreldrerelasjoner som ikke har opphørt (feltet er null eller i fremtiden)
                    (it.folkeregistermetadata?.opphoerstidspunkt?.isAfter(now) ?: true) &&
                    (it.folkeregistermetadata?.gyldighetstidspunkt?.isBefore(now) ?: true)
        }.map {
            Pair(it.relatertPersonsRolle.name, it.relatertPersonsIdent!!)
        }.sortedBy {
            // Sorter rekkefølgen vi sjekker basert på rolle.
            it.first
        },
    ).flatten()

    return vergerOgForeldre
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
