package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
    private val databaseContext: DatabaseContext,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? {
        log.info { "Henter organisasjonsenhet med orgnr: $orgnr" }

        val enhet = transaction(databaseContext) { ctx ->
            ctx.enhetsregisteretStore.hentEnhet(orgnr)
        }

        if (enhet != null) {
            log.info { "Hentet enhet/underenhet med orgnr: $orgnr fra mirror" }
            return enhet
        }

        if (Configuration.dev) {
            // Mock alle mulige organisasjoner som hm-mocks brukte å gjøre
            return mockedOrg(orgnr)
        }

        log.info { "Klarte ikke å finne en organisasjonsenhet eller underenhet for orgnr: $orgnr" }
        return null
    }

    suspend fun hentOrganisasjonsenheter(orgnre: Set<String>): Map<String, Organisasjonsenhet> {
        log.info { "Henter organisasjonsenheter med orgnre: $orgnre" }

        val enheter = transaction(databaseContext) { ctx ->
            ctx.enhetsregisteretStore.hentEnheter(orgnre)
        }.toMutableMap()

        if (Configuration.dev) {
            // Mock alle mulige organisasjoner som hm-mocks brukte å gjøre
            for (orgnr in orgnre) {
                enheter.putAll(mapOf(orgnr to mockedOrg(orgnr)))
            }
        }

        return enheter
    }

    suspend fun organisasjonSlettet(orgnr: String): Boolean {
        kotlin.runCatching {
            val org = kotlin.runCatching { hentOrganisasjonsenhet(orgnr) }.getOrNull()
            if (org != null) {
                return org.slettedato != null
            }

            throw RuntimeException("orgnr=$orgnr kunne ikke bekreftes å være en enhet eller underenhet")
        }.getOrElse {
            log.error(it) { "Kunne ikke sjekke om organisasjonen er slettet" }
        }
        return false
    }

    suspend fun organisasjonSlettetNår(orgnr: String): LocalDate? {
        kotlin.runCatching {
            val org = kotlin.runCatching { hentOrganisasjonsenhet(orgnr) }.getOrNull()
            if (org != null) {
                return org.slettedato
            }

            throw RuntimeException("orgnr=$orgnr kunne ikke bekreftes å være en enhet eller underenhet")
        }.getOrElse {
            log.error(it) { "Kunne ikke sjekke om organisasjonen er slettet" }
        }
        return null
    }

    suspend fun oppdaterMirrorHvisUtdatert(oppdaterUansett: Boolean = false) {
        val sistOppdatert: LocalDateTime? = transaction(databaseContext) {
            it.enhetsregisteretStore.sistOppdatert()
        }

        if (oppdaterUansett || sistOppdatert == null || sistOppdatert.until(LocalDateTime.now(), ChronoUnit.HOURS) >= 24) {
            enhetsregisteretClient.oppdaterMirror()
        }
    }

    private fun mockedOrg(orgnr: String) = Organisasjonsenhet(
        orgnr = orgnr,
        navn = "Brille Verden",
        forretningsadresse = Postadresse(
            adresse =  listOf("Brillevegen 42"),
            poststed =  "Brillestad",
            postnummer =  "6429"
        ),
        naeringskode1 = Næringskode(
            beskrivelse = "Butikkhandel med optiske artikler",
            kode = "47.782",
        ),
    )
}
