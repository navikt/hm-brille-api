package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.jsonMapper
import no.nav.hjelpemidler.brille.kafka.KafkaService
import no.nav.hjelpemidler.brille.pdl.Barn
import no.nav.hjelpemidler.brille.pdl.PdlClient
import no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentbarn.Bostedsadresse
import no.nav.hjelpemidler.brille.pdl.generated.medlemskaphentbarn.DeltBosted
import no.nav.hjelpemidler.brille.redis.RedisClient
import no.nav.hjelpemidler.brille.writePrettyString
import org.slf4j.MDC
import java.time.LocalDate

private val log = KotlinLogging.logger {}
private val sikkerLog = KotlinLogging.logger("tjenestekall")

data class MedlemskapResultat(
    val resultat: MedlemskapResultatResultat,
    val saksgrunnlag: List<Saksgrunnlag>,
)

enum class MedlemskapResultatResultat {
    JA,
    NEI,
    UAVKLART,
}

data class Saksgrunnlag(
    val kilde: SaksgrunnlagKilde,
    val saksgrunnlag: JsonNode,
)

enum class SaksgrunnlagKilde {
    MEDLEMSKAP_BARN,
    PDL,
    LOV_ME,
}

/**
 * FEILSØKING PÅ TVERS AV TJENESTER:
 *
 * Vi lagrer hele saksgrunnlaget som genereres her og som svares ut på kall mot LovMe sin tjeneste "medlemskap-barn". På
 * LovMe sin side lagrer de (per i dag) ikke alt av det som returneres, bare det som finner sted i en av
 * undertjenesten (medlemskap-oppslag). For å kunne feilsøke på tvers av tjenestene må man sende LovMe correlation IDene
 * fra saksgrunnlaget vi lagrer som er merket "correlation-id-subcall-medlemskap". Disse kan de slå opp på og undersøke
 * hvorfor de svarte som de gjorde.
 */

class MedlemskapBarn(
    private val medlemskapClient: MedlemskapClient,
    private val pdlClient: PdlClient,
    private val redisClient: RedisClient,
    private val kafkaService: KafkaService,
) {
    suspend fun sjekkMedlemskapBarn(fnrBarn: String, bestillingsdato: LocalDate): MedlemskapResultat {
        log.info("Sjekker medlemskap for barn")

        val baseCorrelationId = MDC.get(MDC_CORRELATION_ID)
        val saksgrunnlag = mutableListOf(
            Saksgrunnlag(
                kilde = SaksgrunnlagKilde.MEDLEMSKAP_BARN,
                saksgrunnlag = jsonMapper.valueToTree(
                    mapOf(
                        "fnr" to fnrBarn,
                        "bestillingsdato" to bestillingsdato,
                        "correlation-id" to baseCorrelationId
                    )
                )
            )
        )

        // Sjekk om vi nylig har gjort dette oppslaget (ikke i dev. da medlemskapBarn koden er i aktiv utvikling)
        val medlemskapBarnCache = redisClient.medlemskapBarn(fnrBarn, bestillingsdato)
        if (medlemskapBarnCache != null) {
            log.info("Resultat for medlemskapssjekk for barnet funnet i redis-cache")
            sikkerLog.info("Funnet $fnrBarn i cache, returner: $medlemskapBarnCache")
            return medlemskapBarnCache
        }

        // Slå opp pdl informasjon om barnet
        val pdlResponse = pdlClient.medlemskapHentBarn(fnrBarn)
        if (pdlResponse.harAdressebeskyttelse()) {
            sikkerLog.info {
                "Barn har adressebeskyttelse, returnerer positivt medlemskapsresultat"
            }
            val medlemskapResultat = MedlemskapResultat(
                resultat = MedlemskapResultatResultat.JA,
                saksgrunnlag = emptyList(), // vi regner foreløpig med at vi ikke trenger noe saksgrunnlag hvis adressebeskyttelse
            )
            redisClient.setMedlemskapBarn(fnrBarn, bestillingsdato, medlemskapResultat)
            kafkaService.medlemskapFolketrygdenBevist(fnrBarn)
            return medlemskapResultat
        }
        val pdlBarn = pdlResponse.data

        saksgrunnlag.add(
            Saksgrunnlag(
                kilde = SaksgrunnlagKilde.PDL,
                saksgrunnlag = jsonMapper.valueToTree(
                    mapOf(
                        "fnr" to fnrBarn,
                        "pdl" to pdlResponse.rawData,
                    )
                ),
            )
        )

        // Sjekk minimumskravet vårt for å anta medlemskap for barnet i folketrygden.
        if (!sjekkFolkeregistrertAdresseINorge(bestillingsdato, pdlBarn)) {
            // Ingen av de folkeregistrerte bostedsadressene satt på barnet i PDL er en normal norsk adresse (kan
            // feks. fortsatt være utenlandskAdresse/ukjentBosted). Vi kan derfor ikke sjekke medlemskap i noe
            // register eller anta at man har medlemskap basert på at man har en norsk folkereg. adresse. Derfor
            // stopper vi opp behandling tidlig her!
            log.info("Barnet har ikke folkeregistrert adresse i Norge og vi antar derfor at hen ikke er medlem i folketrygden")
            val medlemskapResultat = MedlemskapResultat(MedlemskapResultatResultat.NEI, saksgrunnlag)
            redisClient.setMedlemskapBarn(fnrBarn, bestillingsdato, medlemskapResultat)
            kafkaService.medlemskapFolketrygdenAvvist(fnrBarn)
            return medlemskapResultat
        }

        // Kall LovMe
        val medlemskapResultLovMeJson = kotlin.runCatching {
            medlemskapClient.slåOppMedlemskapBarn(fnrBarn, bestillingsdato, baseCorrelationId)
        }.getOrElse {
            // Logg feilmelding her og kast videre
            log.error(it) { "slåOppMedlemskapBarn feilet med exception" }
            throw it
        }
        saksgrunnlag.add(Saksgrunnlag(
            kilde = SaksgrunnlagKilde.LOV_ME,
            saksgrunnlag = medlemskapResultLovMeJson,
        ))

        val medlemskapResultatLovMe: MedlemskapResultat = jsonMapper.treeToValue(medlemskapResultLovMeJson)
        if (medlemskapResultatLovMe.resultat == MedlemskapResultatResultat.JA) {
            val medlemskapResultatLovMe = medlemskapResultatLovMe.copy(
                saksgrunnlag = saksgrunnlag, // Sakgrunnlaget vårt inneholder LovMe sitt grunnlag rekursivt, samt vårt egent
            )
            redisClient.setMedlemskapBarn(fnrBarn, bestillingsdato, medlemskapResultatLovMe)
            kafkaService.medlemskapFolketrygdenBevist(fnrBarn)
            log.info("Barnets medlemskap verifisert igjennom LovMe-tjenesten (verges-/forelders medlemskap og bolig på samme adresse)")
            return medlemskapResultatLovMe
        }

        // Hvis man kommer sålangt så har man sjekket alle fullmektige, verger og foreldre, og ingen både bor på samme
        // folk.reg. adresse OG har et avklart medlemskap i folketrygden i følge LovMe-tjenesten. Vi svarer derfor at
        // vi har antatt medlemskap bare basert på folkereg. adresse i Norge.
        val medlemskapResultat =
            MedlemskapResultat(
                resultat = MedlemskapResultatResultat.UAVKLART,
                saksgrunnlag = saksgrunnlag
            )
        redisClient.setMedlemskapBarn(fnrBarn, bestillingsdato, medlemskapResultat)
        kafkaService.medlemskapFolketrygdenAntatt(fnrBarn)
        log.info("Barnets medlemskap er antatt pga. folkeregistrert adresse i Norge")
        return medlemskapResultat
    }
}

private fun sjekkFolkeregistrertAdresseINorge(
    bestillingsdato: LocalDate,
    pdlBarn: Barn?,
): Boolean {
    // Avklar folkeregistrert adresse i Norge, ellers stopp behandling.
    val bostedsadresser = pdlBarn?.bostedsadresse ?: listOf()
    val deltBostedBarn = pdlBarn?.deltBosted ?: listOf()
    val aktiveBosteder = slåSammenAktiveBosteder(
        bestillingsdato,
        bostedsadresser,
        deltBostedBarn,
    )

    val finnesFolkeregistrertAdresse =
        aktiveBosteder.any { it.vegadresse != null || it.matrikkeladresse != null || it.ukjentBosted != null }

    try {
        if (!finnesFolkeregistrertAdresse) {
            sikkerLog.info {
                "Fant ingen folkeregistrert adresse for barn:" +
                " bostedsadresser=${jsonMapper.writePrettyString(bostedsadresser)} " +
                " deltBostedBarn=${jsonMapper.writePrettyString(deltBostedBarn)} "
            }
        }
    } catch (e: Exception) {
        log.warn { "Klarte ikke å loggge info om barn uten folkeregistrert adresse" }
    }

    return finnesFolkeregistrertAdresse
}

private fun slåSammenAktiveBosteder(
    bestillingsdato: LocalDate,
    bosted: List<Bostedsadresse>,
    delteBosted: List<DeltBosted>,
): List<Bostedsadresse> {
    // Finn aktive delte bosted for barnet og transformer de til samme format som hoved-folkereg. adresse, så vi kan
    // sjekke alle adresser sammen
    return listOf(
        bosted.filter {
            // Sjekk gyldig fra/til felter
            sjekkBostedsadresseDatoerMotBestillingsdato(bestillingsdato, it)
        },
        delteBosted.filter {
            (it.startdatoForKontrakt.isEqual(bestillingsdato) || it.startdatoForKontrakt.isBefore(bestillingsdato)) &&
            (
                it.sluttdatoForKontrakt == null || it.sluttdatoForKontrakt.isEqual(bestillingsdato) || it.sluttdatoForKontrakt.isAfter(
                    bestillingsdato
                )
            )
        }.map {
            Bostedsadresse(
                gyldigFraOgMed = it.startdatoForKontrakt.atStartOfDay(),
                // Vi oversetter her dato til dato+tid ved å sette gyldigTilOgMed siste sekund denne dagen. Å sette
                // starten av neste dag kan være fristende men vi gjør gyldigTilOgMed-feltet om tilbake til dato andre
                // steder igjen. Så da må det være samme døgn. Derfor gyldig til og med siste sekund det samme døgn.
                gyldigTilOgMed = it.sluttdatoForKontrakt?.plusDays(1)?.atStartOfDay()?.minusSeconds(1),
                vegadresse = it.vegadresse,
                matrikkeladresse = it.matrikkeladresse,
                ukjentBosted = it.ukjentBosted,
            )
        }
    ).flatten()
}

private fun sjekkBostedsadresseDatoerMotBestillingsdato(bestillingsdato: LocalDate, adresse: Bostedsadresse): Boolean {
    return (
        adresse.gyldigFraOgMed == null ||
        adresse.gyldigFraOgMed.toLocalDate().isEqual(bestillingsdato) ||
        adresse.gyldigFraOgMed.toLocalDate().isBefore(bestillingsdato)
    ) && (
        adresse.gyldigTilOgMed == null ||
        adresse.gyldigTilOgMed.toLocalDate().isEqual(bestillingsdato) ||
        adresse.gyldigTilOgMed.toLocalDate().isAfter(bestillingsdato)
    )
}
