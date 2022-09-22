package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.redis.RedisClient

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
    private val redisClient: RedisClient,
) {
    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? {
        log.info { "Henter organisasjonsenhet med orgnr: $orgnr" }

        val cachedEnhet = redisClient.organisasjonsenhet(orgnr)
        if (cachedEnhet != null) {
            log.info { "Hentet orgnr: $orgnr fra cache" }
            return cachedEnhet
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
}
