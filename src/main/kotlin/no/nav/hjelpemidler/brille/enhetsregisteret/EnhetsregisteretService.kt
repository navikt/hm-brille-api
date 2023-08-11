package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.db.DatabaseContext
import no.nav.hjelpemidler.brille.db.transaction
import no.nav.hjelpemidler.brille.redis.RedisClient
import java.time.LocalDate

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
    private val databaseContext: DatabaseContext,
    private val redisClient: RedisClient,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String, cacheBusting: Boolean = false): Organisasjonsenhet? {
        log.info { "Henter organisasjonsenhet med orgnr: $orgnr" }

        /*if (!cacheBusting) {
            val cachedEnhet = redisClient.organisasjonsenhet(orgnr)
            if (cachedEnhet != null) {
                log.info { "Hentet orgnr: $orgnr fra cache" }
                return cachedEnhet
            }
        }

        val enhet = enhetsregisteretClient.hentOrganisasjonsenhet(orgnr)
        if (enhet != null) {
            log.info { "Hentet enhet med orgnr: $orgnr fra tjeneste" }
            redisClient.setOrganisasjonsenhet(orgnr, enhet)
            return enhet
        }

        val underenhet = enhetsregisteretClient.hentUnderenhet(orgnr)
        if (underenhet != null) {
            log.info { "Hentet underenhet med orgnr: $orgnr fra tjeneste" }
            redisClient.setOrganisasjonsenhet(orgnr, underenhet)
            return underenhet
        }*/

        val enhet = transaction(databaseContext) { ctx ->
            ctx.enhetsregisteretStore.hentEnhet(orgnr)
        }

        if (enhet != null) {
            log.info { "Hentet enhet med orgnr: $orgnr fra tjeneste" }
            return enhet
        }

        log.info { "Klarte ikke å finne en organisasjonsenhet eller underenhet for orgnr: $orgnr" }
        return null
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
}
