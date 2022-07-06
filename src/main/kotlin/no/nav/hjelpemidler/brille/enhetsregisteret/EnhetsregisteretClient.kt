package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging

private val log = KotlinLogging.logger { }

class EnhetsregisteretClient(private val baseUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    suspend fun hentOrganisasjonsenhet(organisasjonsnummer: Organisasjonsnummer): Organisasjonsenhet? =
        hentEnhetHelper("$baseUrl/enheter/$organisasjonsnummer")

    suspend fun hentUnderenhet(organisasjonsnummer: Organisasjonsnummer): Organisasjonsenhet? =
        hentEnhetHelper("$baseUrl/underenheter/$organisasjonsnummer")

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
