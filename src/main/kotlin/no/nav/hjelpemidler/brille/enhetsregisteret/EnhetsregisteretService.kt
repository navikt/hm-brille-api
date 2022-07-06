package no.nav.hjelpemidler.brille.enhetsregisteret

import mu.KotlinLogging
import no.nav.hjelpemidler.brille.redis.RedisClient

private val log = KotlinLogging.logger { }

class EnhetsregisteretService(
    private val enhetsregisteretClient: EnhetsregisteretClient,
    private val redisClient: RedisClient
) {

    suspend fun hentOrganisasjonsenhet(organisasjonsnummer: Organisasjonsnummer): Organisasjonsenhet? {
        val cachedOrgEnhet = redisClient.organisasjonsenhet(organisasjonsnummer)
        if (cachedOrgEnhet != null) return cachedOrgEnhet

        val orgenhet = enhetsregisteretClient.hentOrganisasjonsenhet(organisasjonsnummer)
        if (orgenhet != null) {
            redisClient.setOrganisasjonsenhet(organisasjonsnummer, orgenhet)
            return orgenhet
        }

        val underenhet = enhetsregisteretClient.hentUnderenhet(organisasjonsnummer)
        if (underenhet != null) {
            redisClient.setOrganisasjonsenhet(organisasjonsnummer, underenhet)
            return underenhet
        }

        log.info { "Klarte ikke å finne en organisasjonsenhet eller underenhet for orgnr $organisasjonsnummer" }
        return null
    }
}
