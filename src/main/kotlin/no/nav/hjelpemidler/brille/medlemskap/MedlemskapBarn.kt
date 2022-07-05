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
import no.nav.hjelpemidler.brille.pdl.ForelderBarnRelasjonRolle
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.PdlPersonResponse
import no.nav.hjelpemidler.brille.pdl.validerPdlOppslag
import no.nav.hjelpemidler.brille.redis.RedisClient
import org.slf4j.MDC
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

        withLoggingContext(
            mapOf()
        ) {
            // Sjekk om vi nylig har gjort dette oppslaget (ikke i dev. da medlemskapBarn koden er i aktiv utvikling)
            if (Configuration.profile != Profile.DEV) {
                val medlemskapBarnCache = redisClient.medlemskapBarn(fnrBarn)
                if (medlemskapBarnCache != null) {
                    tjenestelogg.info("Funnet $fnrBarn i cache, returner: $medlemskapBarnCache")
                    return@runBlocking medlemskapBarnCache
                }
            }

            log.info("Sjekker medlemskap for barn")

            // Slå opp pdl informasjon om barnet
            val pdlBarn = pdlClient.medlemskapHentBarn(fnrBarn)
            validerPdlOppslag(pdlBarn)

            saksgrunnlag.add(
                Saksgrunnlag(
                    kilde = SaksgrunnlagType.PDL,
                    saksgrunnlag = jsonMapper.valueToTree(
                        mapOf(
                            "fnr" to fnrBarn,
                            "pdl" to pdlBarn,
                        )
                    ),
                )
            )

            log.debug("PDL response: ${jsonMapper.writeValueAsString(pdlBarn)}")

            if (!sjekkFolkeregistrertAdresseINorge(pdlBarn)) {
                // Ingen av de folkeregistrerte bostedsadressene satt på barnet i PDL er en normal norsk adresse (kan
                // feks. fortsatt være utenlandskAdresse/ukjentBosted). Vi kan derfor ikke sjekke medlemskap i noe
                // register eller anta at man har medlemskap basert på at man har en norsk folkereg. adresse. Derfor
                // stopper vi opp behandling tidlig her!
                val medlemskapResultat = MedlemskapResultat(false, false, false, saksgrunnlag)
                redisClient.setMedlemskapBarn(fnrBarn, medlemskapResultat)
                return@runBlocking medlemskapResultat
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
                    validerPdlOppslag(pdlVergeEllerForelder)

                    saksgrunnlag.add(
                        Saksgrunnlag(
                            kilde = SaksgrunnlagType.PDL,
                            saksgrunnlag = jsonMapper.valueToTree(
                                mapOf(
                                    "rolle" to rolle,
                                    "fnr" to fnrVergeEllerForelder,
                                    "pdl" to pdlVergeEllerForelder,
                                )
                            ),
                        )
                    )

                    log.debug("PDL response verge/forelder: ${jsonMapper.writeValueAsString(pdlVergeEllerForelder)}")

                    // TODO: Valider samme adresse som barn
                    if (harSammeAdresse(pdlBarn, pdlVergeEllerForelder)) {
                        log.info("Verge/forelder deler folkeregistrert adresse med barnet, sjekker medlemskap i folketrygden for verge/forelder mot LovMe")

                        // TODO: Sjekk medlemskap:
                        val medlemskap =
                            medlemskapClient.slåOppMedlemskap(fnrVergeEllerForelder, correlationIdMedlemskap)
                        log.debug("LovMe response verge/forelder: ${jsonMapper.writeValueAsString(medlemskap)}")

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

                        val medlemskapResponse: MedlemskapResponse = jsonMapper.treeToValue(medlemskap)
                        log.debug(
                            "LovMe response verge/forelder (parsed): ${
                            jsonMapper.writeValueAsString(
                                medlemskapResponse
                            )
                            }"
                        )

                        if (medlemskapResponse.resultat.svar == MedlemskapResponseResultatSvar.JA) {
                            log.debug("Medlemskap verifisert! Hopper over de andre i listen (hvis det var flere man kunne sjekke)")
                            return@runBlocking MedlemskapResultat(
                                true,
                                medlemskapBevist = true,
                                uavklartMedlemskap = false,
                                saksgrunnlag = saksgrunnlag
                            )
                        } else if (medlemskapResponse.resultat.svar == MedlemskapResponseResultatSvar.UAVKLART) {
                            log.debug("Medlemskap for verge/forelder er uavklart i følge LovMe, fortsetter å slå opp andre i listen om vi har flere å sjekke")
                        } else {
                            log.debug("Medlemskap avvist for vege/forelder i følge LovMe, fortsetter å slå opp andre i listen om vi har flere å sjekke")
                        }
                    } else {
                        log.info("Verge/forelder delte ikke folkeregistrert adresse med barnet")
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
            log.debug("medlemskapResultat: ${jsonMapper.writeValueAsString(medlemskapResultat)}")
            medlemskapResultat
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

private fun harSammeAdresse(barn: PdlPersonResponse, annen: PdlPersonResponse): Boolean {
    val bostedsadresserBarn = barn.data?.hentPerson?.bostedsadresse ?: listOf()
    val bostedsadresserAnnen = annen.data?.hentPerson?.bostedsadresse ?: listOf()

    for (adresseBarn in bostedsadresserBarn) {
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
