package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.redis.RedisClient

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
    private val redisClient: RedisClient,
) {

    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? {
        val cachedEnhet = redisClient.organisasjonsenhet(orgnr)
        if (cachedEnhet != null) return cachedEnhet

        val enhet = enhetsregisteretClient.hentOrganisasjonsenhet(orgnr)
        if (enhet != null) {
            redisClient.setOrganisasjonsenhet(orgnr, enhet)
            return enhet
        }

        val underenhet = enhetsregisteretClient.hentUnderenhet(orgnr)
        if (underenhet != null) {
            redisClient.setOrganisasjonsenhet(orgnr, underenhet)
            return underenhet
        }

        log.info { "Klarte ikke å finne en organisasjonsenhet eller underenhet for orgnr: $orgnr" }
        return null
    }
}
