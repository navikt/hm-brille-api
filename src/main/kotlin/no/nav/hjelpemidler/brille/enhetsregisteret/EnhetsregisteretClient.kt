package no.nav.hjelpemidler.brille.enhetsregisteret

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.http.createHttpClient

private val log = KotlinLogging.logger { }

class EnhetsregisteretClient(props: Configuration.EnhetsregisteretProperties) {
    private val baseUrl = props.baseUrl
    private val client = createHttpClient()

    suspend fun hentOrganisasjonsenhet(orgnr: String): Organisasjonsenhet? =
        hentEnhetHelper("$baseUrl/enheter/$orgnr")

    suspend fun hentUnderenhet(orgnr: String): Organisasjonsenhet? =
        hentEnhetHelper("$baseUrl/underenheter/$orgnr")

    private suspend fun hentEnhetHelper(url: String): Organisasjonsenhet? {
        try {
            log.info { "Henter enhet med url: $url" }
            return withContext(Dispatchers.IO) {
                val response = client.get(url)
                when (response.status) {
                    HttpStatusCode.OK -> response.body()
                    HttpStatusCode.NotFound -> null
                    else -> throw EnhetsregisteretClientException("Uventet svar fra tjeneste: ${response.status}", null)
                }
            }
        } catch (e: ResponseException) {
            throw EnhetsregisteretClientException("Feil under henting av organisasjonsenhet", e)
        }
    }
}
