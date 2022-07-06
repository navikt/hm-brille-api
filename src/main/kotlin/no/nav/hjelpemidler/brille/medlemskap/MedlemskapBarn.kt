package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.Profile
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.pdl.Bostedsadresse
import no.nav.hjelpemidler.brille.pdl.DeltBosted
import no.nav.hjelpemidler.brille.pdl.ForelderBarnRelasjonRolle
import no.nav.hjelpemidler.brille.pdl.MotpartsRolle
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.redis.RedisClient
import org.slf4j.MDC
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val tjenestelogg = KotlinLogging.logger("tjenestekall")

data class MedlemskapResultat(
    val kanSøke: Boolean, // Ja eller antatt pga. folkereg. addresse i norge
    val medlemskapBevist: Boolean,
    val uavklartMedlemskap: Boolean,
    val saksgrunnlag: List<Saksgrunnlag>,
)

data class Saksgrunnlag(
    val kilde: SaksgrunnlagType,
    val saksgrunnlag: JsonNode,
)

enum class SaksgrunnlagType {
    MEDLEMSKAP_BARN,
    PDL,
    LOV_ME,
}

class MedlemskapBarn(
    private val medlemskapClient: MedlemskapClient,
    private val pdlClient: PdlClient,
    private val redisClient: RedisClient,
) {
    fun sjekkMedlemskapBarn(fnrBarn: String): MedlemskapResultat = runBlocking {
        log.info("Sjekker medlemskap for barn")

        val baseCorrelationId = MDC.get(MDC_CORRELATION_ID)
        val saksgrunnlag = mutableListOf(
            Saksgrunnlag(
                kilde = SaksgrunnlagType.MEDLEMSKAP_BARN,
                saksgrunnlag = jsonMapper.valueToTree(
                    mapOf(
                        "fnr" to fnrBarn,
                        "correlation-id" to baseCorrelationId
                    )
                )
            )
        )

        // Sjekk om vi nylig har gjort dette oppslaget (ikke i dev. da medlemskapBarn koden er i aktiv utvikling)
        if (Configuration.profile != Profile.DEV) {
            val medlemskapBarnCache = redisClient.medlemskapBarn(fnrBarn)
            if (medlemskapBarnCache != null) {
                log.info("Resultat for medlemskapssjekk for barnet funnet i redis-cache")
                tjenestelogg.info("Funnet $fnrBarn i cache, returner: $medlemskapBarnCache")
                return@runBlocking medlemskapBarnCache
            }
        }

        // Slå opp pdl informasjon om barnet
        val pdlResponse = pdlClient.medlemskapHentBarn(fnrBarn)
        val pdlBarn = pdlResponse.pdlPersonResponse

        log.debug("PDL BARN: ${jsonMapper.writeValueAsString(pdlBarn)}")

        saksgrunnlag.add(
            Saksgrunnlag(
                kilde = SaksgrunnlagType.PDL,
                saksgrunnlag = jsonMapper.valueToTree(
                    mapOf(
                        "fnr" to fnrBarn,
                        "pdl" to pdlResponse.saksgrunnlag,
                    )
                ),
            )
        )

        if (!sjekkFolkeregistrertAdresseINorge(pdlBarn)) {
            // Ingen av de folkeregistrerte bostedsadressene satt på barnet i PDL er en normal norsk adresse (kan
            // feks. fortsatt være utenlandskAdresse/ukjentBosted). Vi kan derfor ikke sjekke medlemskap i noe
            // register eller anta at man har medlemskap basert på at man har en norsk folkereg. adresse. Derfor
            // stopper vi opp behandling tidlig her!
            log.info("Barnet har ikke folkeregistrert adresse i Norge og vi antar derfor at hen ikke er medlem i folketrygden")
            val medlemskapResultat = MedlemskapResultat(false, false, false, saksgrunnlag)
            redisClient.setMedlemskapBarn(fnrBarn, medlemskapResultat)
            return@runBlocking medlemskapResultat
        }

        val prioritertListe = prioriterFullmektigeVergerOgForeldreForSjekkMotMedlemskap(pdlBarn)
        log.debug("prioritertListe = ${jsonMapper.writeValueAsString(prioritertListe)}")

        for ((rolle, fnrVergeEllerForelder) in prioritertListe) {
            val correlationIdMedlemskap = "$baseCorrelationId+${UUID.randomUUID()}"
            withLoggingContext(
                mapOf(
                    "correlation-id-subcall-medlemskap" to correlationIdMedlemskap,
                    "rolle" to rolle,
                )
            ) {
                // Slå opp verge / foreldre i PDL for å sammenligne folkeregistrerte adresse
                val pdlResponseVerge = pdlClient.medlemskapHentVergeEllerForelder(fnrVergeEllerForelder)
                val pdlVergeEllerForelder = pdlResponseVerge.pdlPersonResponse

                saksgrunnlag.add(
                    Saksgrunnlag(
                        kilde = SaksgrunnlagType.PDL,
                        saksgrunnlag = jsonMapper.valueToTree(
                            mapOf(
                                "rolle" to rolle,
                                "fnr" to fnrVergeEllerForelder,
                                "pdl" to pdlResponseVerge.saksgrunnlag,
                            )
                        ),
                    )
                )

                if (harSammeAdresse(pdlBarn, pdlVergeEllerForelder)) {
                    val medlemskap =
                        medlemskapClient.slåOppMedlemskap(fnrVergeEllerForelder, correlationIdMedlemskap)

                    saksgrunnlag.add(
                        Saksgrunnlag(
                            kilde = SaksgrunnlagType.LOV_ME,
                            saksgrunnlag = jsonMapper.valueToTree(
                                mapOf(
                                    "rolle" to rolle,
                                    "fnr" to fnrVergeEllerForelder,
                                    "lov_me" to medlemskap,
                                    "correlation-id-subcall-medlemskap" to correlationIdMedlemskap,
                                )
                            ),
                        )
                    )

                    when (jsonMapper.treeToValue<MedlemskapResponse>(medlemskap).resultat.svar) {
                        MedlemskapResponseResultatSvar.JA -> {
                            log.info("Barnets medlemskap verifisert igjennom verges-/forelders medlemskap og bolig på samme adresse")
                            val medlemskapResultat = MedlemskapResultat(
                                true,
                                medlemskapBevist = true,
                                uavklartMedlemskap = false,
                                saksgrunnlag = saksgrunnlag
                            )
                            redisClient.setMedlemskapBarn(fnrBarn, medlemskapResultat)
                            log.debug("medlemskapResultat: ${jsonMapper.writeValueAsString(medlemskapResultat)}")
                            return@runBlocking medlemskapResultat
                        }
                        else -> { /* Sjekk de andre */
                        }
                    }
                }
            }
        }

        // Hvis man kommer sålangt så har man sjekket alle verger og foreldre, og ingen både bor på samme folk.reg.
        // adresse OG har et avklart medlemskap i folketrygden i følge LovMe-tjenesten.
        val medlemskapResultat =
            MedlemskapResultat(
                true,
                medlemskapBevist = false,
                uavklartMedlemskap = true,
                saksgrunnlag = saksgrunnlag
            )
        redisClient.setMedlemskapBarn(fnrBarn, medlemskapResultat)
        log.info("Barnets medlemskap er antatt pga. folkeregistrert adresse i Norge")
        log.debug("medlemskapResultat: ${jsonMapper.writeValueAsString(medlemskapResultat)}")
        medlemskapResultat
    }
}

private fun sjekkFolkeregistrertAdresseINorge(pdlBarn: PdlPersonResponse): Boolean {
    // TODO: Avklar folkeregistrert adresse i Norge, ellers stopp behandling?
    val bostedsadresser = pdlBarn.data?.hentPerson?.bostedsadresse ?: listOf()
    val deltBostedBarn = pdlBarn.data?.hentPerson?.deltBosted ?: listOf()
    return slåSammenMedAktiveDelteBosted(bostedsadresser, deltBostedBarn).any { it.vegadresse != null || it.matrikkeladresse != null }
}

private fun prioriterFullmektigeVergerOgForeldreForSjekkMotMedlemskap(pdlBarn: PdlPersonResponse): List<Pair<String, String>> {
    // Lag en liste i prioritert rekkefølge for hvem vi skal slå opp i medlemskap-oppslag tjenesten. Her
    // prioriterer vi først verger (under antagelse om at foreldre kanskje har mistet forelderansvaret hvis
    // barnet har fått en annen verge). Etter det kommer foreldre relasjoner prioritert etter rolle.

    val fullmakt = pdlBarn.data?.hentPerson?.fullmakt ?: listOf()
    val vergemaalEllerFremtidsfullmakt = pdlBarn.data?.hentPerson?.vergemaalEllerFremtidsfullmakt ?: listOf()
    val foreldreAnsvar = pdlBarn.data?.hentPerson?.foreldreansvar ?: listOf()
    val foreldreBarnRelasjon = pdlBarn.data?.hentPerson?.forelderBarnRelasjon ?: listOf()

    val now = LocalDateTime.now()
    val fullmektigeVergerOgForeldre: List<Pair<String, String>> = listOf(

        fullmakt.filter {
            // Fullmakter har alltid fom. og tom. datoer for gyldighet, sjekk mot dagens dato
            (it.gyldigFraOgMed.isEqual(now.toLocalDate()) || it.gyldigFraOgMed.isBefore(now.toLocalDate())) &&
                (it.gyldigTilOgMed.isEqual(now.toLocalDate()) || it.gyldigTilOgMed.isAfter(now.toLocalDate())) &&
                // Fullmektig ovenfor barnet
                it.motpartsRolle == MotpartsRolle.FULLMEKTIG
            // TODO: Vurder å sjekke "omraader" feltet, og begrense til visse typer fullmektige
        }.map {
            Pair("FULLMAKT-${it.motpartsRolle}", it.motpartsPersonident)
        },

        vergemaalEllerFremtidsfullmakt.filter {
            // Sjekk om vi har et fnr for vergen ellers kan vi ikke slå personen opp i medlemskap-oppslag
            it.vergeEllerFullmektig.motpartsPersonident != null &&
                // Bare se på vergerelasjoner som ikke har opphørt (feltet er null eller i fremtiden)
                (it.folkeregistermetadata?.opphoerstidspunkt?.isAfter(now) ?: true) &&
                (it.folkeregistermetadata?.gyldighetstidspunkt?.isBefore(now) ?: true)
        }.map {
            Pair("VERGE-${it.type ?: "ukjent-type"}", it.vergeEllerFullmektig.motpartsPersonident!!)
        },

        // TODO: Trenger vi begge disse under?

        foreldreAnsvar.filter {
            // Må ha et fnr vi kan slå opp på
            it.ansvarlig != null &&
                // Bare se på foreldreansvar som ikke har opphørt (feltet er null eller i fremtiden)
                (it.folkeregistermetadata?.opphoerstidspunkt?.isAfter(now) ?: true) &&
                (it.folkeregistermetadata?.gyldighetstidspunkt?.isBefore(now) ?: true)
        }.map {
            Pair("FORELDER_ANSVAR-${it.ansvar ?: "ukjent"}", it.ansvarlig!!)
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

    // Skip duplikater. Man kan ha flere roller ovenfor et barn samtidig (foreldre-relasjon og foreldre-ansvar). Og det
    // blir fort rot i dolly (i dev) når man oppretter og endrer brukere (masse dupikate relasjoner osv). Skipper derfor
    // her duplikate fnr da det ikke henger på grep å slå opp samme person flere ganger
    val fnrSeen = mutableMapOf<String, Boolean>()
    return fullmektigeVergerOgForeldre.filter {
        if (fnrSeen[it.second] == null) {
            fnrSeen[it.second] = true
            true
        } else {
            false
        }
    }
}

private fun harSammeAdresse(barn: PdlPersonResponse, annen: PdlPersonResponse): Boolean {
    val bostedsadresserBarn = barn.data?.hentPerson?.bostedsadresse ?: listOf()
    val deltBostedBarn = barn.data?.hentPerson?.deltBosted ?: listOf()
    val bostedsadresserAnnen = annen.data?.hentPerson?.bostedsadresse ?: listOf()

    for (adresseBarn in slåSammenMedAktiveDelteBosted(bostedsadresserBarn, deltBostedBarn)) {
        if (adresseBarn.matrikkeladresse?.matrikkelId != null) {
            // Det eneste vi kan sammenligne her er om matrikkel IDen matcher
            if (bostedsadresserAnnen.mapNotNull { it.matrikkeladresse?.matrikkelId }
                .contains(adresseBarn.matrikkeladresse.matrikkelId)
            ) {
                // Fant overlappende matrikkelId mellom barn og annen part
                log.debug("harSammeAdresse: fant overlappende matrikkelId mellom barn og annen part")
                return true
            }
        } else if (adresseBarn.vegadresse != null) {
            val adr1 = adresseBarn.vegadresse
            // Sjekk at vi i det minste har ét eller flere av disse feltene, slik at vi ikke aksepterer at barn og annen
            // part begge har alle feltene == null.
            // TODO: Vurder om dette funker eller om vi trenger noe fuzzy-søk lignende (små/store bokstaver,
            //  ufullstendig adresse på en av de)
            if (adr1.matrikkelId != null ||
                adr1.adressenavn != null ||
                adr1.husnummer != null ||
                adr1.husbokstav != null ||
                adr1.postnummer != null ||
                adr1.tilleggsnavn != null
            ) {
                if (bostedsadresserAnnen
                    .mapNotNull { it.vegadresse }
                    .any { adr2 ->
                        adr1.matrikkelId == adr2.matrikkelId &&
                            adr1.adressenavn == adr2.adressenavn &&
                            adr1.husnummer == adr2.husnummer &&
                            adr1.husbokstav == adr2.husbokstav &&
                            adr1.postnummer == adr2.postnummer &&
                            adr1.tilleggsnavn == adr2.tilleggsnavn
                    }
                ) {
                    // Fant overlappende vegadresse mellom barn og annen part
                    log.debug("harSammeAdresse: fant overlappende vegadresse mellom barn og annen part")
                    return true
                }
            }
        } else {
            log.debug("harSammeAdresse: kan ikke sammenligne en bostedsadresse av annen type (utenlandsk, etc.).")
        }
    }

    // Matchende adresse ikke funnet
    log.debug("harSammeAdresse: fant ikke noe overlappende adresse mellom barn og annen part")
    return false
}

private fun slåSammenMedAktiveDelteBosted(
    base: List<Bostedsadresse>,
    delteBosted: List<DeltBosted>
): List<Bostedsadresse> {
    // Finn aktive delte bosted for barnet og transformer de til samme format som hoved-folkereg. adresse, så vi kan
    // sjekke alle adresser sammen
    val now = LocalDate.now()
    return listOf(
        base,
        delteBosted.filter {
            (it.startdatoForKontrakt.isEqual(now) || it.startdatoForKontrakt.isBefore(now)) &&
                (it.sluttdatoForKontrakt == null || it.sluttdatoForKontrakt.isEqual(now) || it.sluttdatoForKontrakt.isAfter(now))
        }.map {
            Bostedsadresse(
                vegadresse = it.vegadresse,
                matrikkeladresse = it.matrikkeladresse,
            )
        }
    ).flatten()
}

private data class MedlemskapResponse(
    val resultat: MedlemskapResponseResultat,
)

private data class MedlemskapResponseResultat(
    val regelId: MedlemskapResponseResultatRegelId,
    val svar: MedlemskapResponseResultatSvar,
    val årsaker: List<MedlemskapResultatÅrsaker>,
)

private enum class MedlemskapResponseResultatRegelId {
    REGEL_MEDLEM_KONKLUSJON
}

private enum class MedlemskapResponseResultatSvar {
    JA, UAVKLART, NEI
}

private data class MedlemskapResultatÅrsaker(
    val regelId: String,
    val avklaring: String,
    val svar: MedlemskapResponseResultatSvar,
    val begrunnelse: String,
)
