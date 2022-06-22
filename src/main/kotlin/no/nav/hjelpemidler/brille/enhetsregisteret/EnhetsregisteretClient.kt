package no.nav.hjelpemidler.brille.enhetsregisteret

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging

class EnhetsregisteretClient(private val baseUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    suspend fun hentOrganisasjonsenhet(organisasjonsnummer: Organisasjonsnummer): Organisasjonsenhet = runCatching {
        val url = "$baseUrl/enheter/$organisasjonsnummer"
        log.info { "Henter organisasjonsenhet med url: $url" }
        val response = client.get(url)
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        throw EnhetsregisteretClientException("Uventet svar fra tjeneste: ${response.status}", null)
    }.getOrElse { throw EnhetsregisteretClientException("Feil under henting av organisasjonsenhet", it) }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
