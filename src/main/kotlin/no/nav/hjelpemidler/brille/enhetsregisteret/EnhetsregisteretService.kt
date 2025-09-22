package no.nav.hjelpemidler.brille.enhetsregisteret

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.configuration.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val databaseContext: DatabaseContext,
    private val enhetsregisteretClient: EnhetsregisteretClient,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? {
        log.info { "Henter organisasjonsenhet med orgnr: $orgnr" }

        val enhet = transaction(databaseContext) { ctx ->
            ctx.enhetsregisteretStore.hentEnhet(orgnr)
        }
            ?: enhetsregisteretClient.hentEnhet(orgnr) // Fall tilbake på web apiet: enhetsregister-mirror inneholder feks. ikke slettede enheter

        if (enhet != null) {
            log.info { "Hentet enhet/underenhet med orgnr: $orgnr fra mirror" }
            return enhet
        }

        if (Environment.current.isDev) {
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

        val manglendeEnheter = orgnre.filter { !enheter.containsKey(it) }.let { mangler ->
            if (mangler.isNotEmpty()) {
                ", men ${mangler.count()} enheter ble ikke funnet og blir derfor slått opp i enhetsregisterets api. Disse er: $mangler"
            } else {
                ""
            }
        }
        log.info { "Hentet ${enheter.count()} enheter fra enhetsregister-mirror$manglendeEnheter" }

        val enheterFraTilbakefallsLøsning = orgnre.filter { !enheter.containsKey(it) }.mapNotNull { orgnr ->
            enhetsregisteretClient.hentEnhet(orgnr) // Fall tilbake på web apiet: enhetsregister-mirror inneholder feks. ikke slettede enheter
        }.groupBy { it.orgnr }.mapValues { it.value.first() }
        enheter.putAll(enheterFraTilbakefallsLøsning)

        if (Environment.current.isDev) {
            // Mock alle mulige organisasjoner som hm-mocks brukte å gjøre
            for (orgnr in orgnre) {
                enheter.putAll(mapOf(orgnr to mockedOrg(orgnr)))
            }
        }

        orgnre.filter { !enheter.containsKey(it) }.let { mangler ->
            if (mangler.isNotEmpty()) {
                log.warn { "Noen orgnre ble aldri funnet: $mangler" }
            }
        }

        return enheter
    }

    suspend fun organisasjonSlettet(orgnr: String): Boolean {
        val org =
            runCatching { transaction(databaseContext) { ctx -> ctx.enhetsregisteretStore.hentEnhet(orgnr) } }.getOrNull()
                ?: enhetsregisteretClient.hentEnhet(orgnr)
                ?: throw java.lang.RuntimeException("Kunne ikke sjekke om organisasjonen er slettet: orgnr=$orgnr kunne ikke bekreftes å være en enhet eller underenhet")
        return org.slettedato != null
    }

    suspend fun organisasjonSlettetNår(orgnr: String): LocalDate? {
        val org =
            runCatching { transaction(databaseContext) { ctx -> ctx.enhetsregisteretStore.hentEnhet(orgnr) } }.getOrNull()
                ?: enhetsregisteretClient.hentEnhet(orgnr)
                ?: throw RuntimeException("Kunne ikke sjekke om organisasjonen er slettet: orgnr=$orgnr kunne ikke bekreftes å være en enhet eller underenhet")
        return org.slettedato
    }

    suspend fun oppdaterMirrorHvisUtdatert(oppdaterUansett: Boolean = false) {
        val sistOppdatert: LocalDateTime? = transaction(databaseContext) {
            it.enhetsregisteretStore.sistOppdatert()
        }

        if (oppdaterUansett || sistOppdatert == null || sistOppdatert.until(
                LocalDateTime.now(),
                ChronoUnit.HOURS,
            ) > 20
        ) {
            enhetsregisteretClient.oppdaterMirror()
        }
    }

    private fun mockedOrg(orgnr: String) = Organisasjonsenhet(
        orgnr = orgnr,
        navn = "Brille Verden",
        forretningsadresse = Postadresse(
            adresse = listOf("Brillevegen 42"),
            poststed = "Brillestad",
            postnummer = "6429",
        ),
        naeringskode1 = Næringskode(
            beskrivelse = "Butikkhandel med optiske artikler",
            kode = "47.782",
        ),
    )
}
