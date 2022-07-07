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
import java.time.LocalDateTime
import java.util.UUID

private val log = KotlinLogging.logger {}
private val tjenestelogg = KotlinLogging.logger("tjenestekall")

data class MedlemskapResultat(
    val medlemskapBevist: Boolean,
    val uavklartMedlemskap: Boolean,
    val saksgrunnlag: List<Saksgrunnlag>,
)

data class Saksgrunnlag(
    val kilde: SaksgrunnlagKilde,
    val saksgrunnlag: JsonNode,
)

enum class SaksgrunnlagKilde {
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
                kilde = SaksgrunnlagKilde.MEDLEMSKAP_BARN,
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
                kilde = SaksgrunnlagKilde.PDL,
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
            val medlemskapResultat = MedlemskapResultat(false, false, saksgrunnlag)
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
                        kilde = SaksgrunnlagKilde.PDL,
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
                            kilde = SaksgrunnlagKilde.LOV_ME,
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
    // Avklar folkeregistrert adresse i Norge, ellers stopp behandling?
    val bostedsadresser = pdlBarn.data?.hentPerson?.bostedsadresse ?: listOf()
    val deltBostedBarn = pdlBarn.data?.hentPerson?.deltBosted ?: listOf()
    return slåSammenAktiveBosteder(bostedsadresser, deltBostedBarn).any { it.vegadresse != null || it.matrikkeladresse != null || it.ukjentBosted != null }
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
        }.map {
            Pair("FULLMEKTIG-${it.motpartsRolle}", it.motpartsPersonident)
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
            // Må ha et fnr vi kan slå opp på. Dette bekrefter også at relasjonen gjelder en forelder, ikke at oppslått
            // barn har foreldreansvar for noen:
            // "Alltid tomt ved oppslag på ansvarlig." ref.: https://pdldocs-navno.msappproxy.net/ekstern/index.html#_foreldreansvar
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
            Pair("FORELDER_BARN_RELASJON-${it.relatertPersonsRolle.name}", it.relatertPersonsIdent!!)
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
    // Vi sammenligner adresser for å se om barn og foresatte (foreldre, verger, fullmektige) bor sammen. For slike
    // formål anbefaler PDL at man sammenligner matrikkelId og bruksenhetsnummeret. Begge disse datapunktene skal ha
    // relativt god kvalitet. Da vi har toleranse for småfeil så godtar vi at minimum bare matrikkelId er lik hvis det
    // ikke er satt bruksenhetsnummer på noen av de vi sammenligner. Men om én av de har bruksenhetsnummer må begge ha
    // det og de må være like. Les mer her: https://pdldocs-navno.msappproxy.net/ekstern/index.html#_tps_5.

    // Obs: for barn med aktive delt bosted kontrakter mellom feks. skilte foreldre, så anser vi begge adressene som
    // folkeregistrerte adresser (gitt at kontrakten er aktiv).

    // Barnets adresser
    val bostedsadresserBarn = barn.data?.hentPerson?.bostedsadresse ?: listOf()
    val deltBostedBarn = barn.data?.hentPerson?.deltBosted ?: listOf()

    // Sammenlignes med "annen"
    val now = LocalDateTime.now()
    val bostedsadresserAnnen = (annen.data?.hentPerson?.bostedsadresse ?: listOf()).filter {
        // TODO: Hva med gyldighetsdatoer her?
        (it.gyldigFraOgMed == null || it.gyldigFraOgMed.isBefore(now)) &&
            (it.gyldigTilOgMed == null || it.gyldigTilOgMed.isAfter(now))
    }

    // For hver adresse barnet har (vanlig og delt), så sammenligner vi basert på type mot den andre partens adresser
    // av samme type
    for (adresseBarn in slåSammenAktiveBosteder(bostedsadresserBarn, deltBostedBarn)) {
        when {
            adresseBarn.matrikkeladresse != null -> {
                val madr1 = adresseBarn.matrikkeladresse
                if (madr1.matrikkelId != null) {
                    if (bostedsadresserAnnen
                        .mapNotNull { it.matrikkeladresse }
                        .any { madr2 ->
                            madr1.matrikkelId == madr2.matrikkelId &&
                                madr1.bruksenhetsnummer == madr2.bruksenhetsnummer
                        }
                    ) {
                        // Fant overlappende matrikkelId mellom barn og annen part
                        log.debug("harSammeAdresse: fant overlappende matrikkelId/bruksenhetsnummer (matrikkeladresse) mellom barn og annen part")
                        return true
                    }
                }
            }

            adresseBarn.vegadresse != null -> {
                val adr1 = adresseBarn.vegadresse
                if (adr1.matrikkelId != null) {
                    if (bostedsadresserAnnen
                        .mapNotNull { it.vegadresse }
                        .any { adr2 ->
                            adr1.matrikkelId == adr2.matrikkelId &&
                                adr1.bruksenhetsnummer == adr2.bruksenhetsnummer
                        }
                    ) {
                        // Fant overlappende vegadresse mellom barn og annen part
                        log.debug("harSammeAdresse: fant overlappende matrikkelId/bruksenhetsnummer (vegadresse) mellom barn og annen part")
                        return true
                    }
                }
            }

            else -> {
                // Hvis adresse-typen er ukjent så er det ikke noe vi kan sammenlige med andre, så vi skipper den her.
                log.debug("harSammeAdresse: kan ikke sammenligne en bostedsadresse av annen type (utenlandsk, etc.).")
            }
        }
    }

    // Matchende adresse ikke funnet
    log.debug("harSammeAdresse: fant ikke noe overlappende adresse mellom barn og annen part")
    return false
}

private fun slåSammenAktiveBosteder(
    bosted: List<Bostedsadresse>,
    delteBosted: List<DeltBosted>
): List<Bostedsadresse> {
    // Finn aktive delte bosted for barnet og transformer de til samme format som hoved-folkereg. adresse, så vi kan
    // sjekke alle adresser sammen
    val now = LocalDateTime.now()
    return listOf(
        bosted.filter {
            // Sjekk gyldig fra/til felter
            (it.gyldigFraOgMed == null || it.gyldigFraOgMed.isBefore(now)) &&
                (it.gyldigTilOgMed == null || it.gyldigTilOgMed.isAfter(now))
        },
        delteBosted.filter {
            (it.startdatoForKontrakt.isEqual(now.toLocalDate()) || it.startdatoForKontrakt.isBefore(now.toLocalDate())) &&
                (it.sluttdatoForKontrakt == null || it.sluttdatoForKontrakt.isEqual(now.toLocalDate()) || it.sluttdatoForKontrakt.isAfter(now.toLocalDate()))
        }.map {
            Bostedsadresse(
                gyldigFraOgMed = it.startdatoForKontrakt.atStartOfDay(),
                gyldigTilOgMed = it.sluttdatoForKontrakt?.plusDays(1)?.atStartOfDay(),
                vegadresse = it.vegadresse,
                matrikkeladresse = it.matrikkeladresse,
                ukjentBosted = it.ukjentBosted,
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
