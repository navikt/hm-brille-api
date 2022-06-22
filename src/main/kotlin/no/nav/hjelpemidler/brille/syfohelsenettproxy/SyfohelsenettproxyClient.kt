package no.nav.hjelpemidler.brille.syfohelsenettproxy

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging

class SyfohelsenettproxyClient(private val baseUrl: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
    }

    suspend fun hentBehandler(fnr: String): Behandler = runCatching {
        val url = "$baseUrl/behandler"
        log.info { "Henter behandler data med url: $url" }
        val response = client.get(url) {
            headers["behandlerFnr"] = fnr
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
    }.getOrElse { throw SyfohelsenettproxyClientException("Feil under henting av behandler data", it) }

    suspend fun hentBehandlerMedHprNummer(hprnr: String): Behandler = runCatching {
        val url = "$baseUrl/behandlerMedHprNummer"
        log.info { "Henter behandler data med url: $url" }
        val response = client.get(url) {
            headers["hprNummer"] = hprnr
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
    }.getOrElse { throw SyfohelsenettproxyClientException("Feil under henting av behandler data", it) }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
