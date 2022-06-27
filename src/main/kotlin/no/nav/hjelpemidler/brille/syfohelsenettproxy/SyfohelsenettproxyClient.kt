package no.nav.hjelpemidler.brille.syfohelsenettproxy

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.azuread.AzureAdClient

private val log = KotlinLogging.logger { }

class SyfohelsenettproxyClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: AzureAdClient,
    engine: HttpClientEngine = CIO.create(),
) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        install(Auth) {
            bearer {
                loadTokens { azureAdClient.getToken(scope).toBearerTokens() }
                refreshTokens { null }
                sendWithoutRequest { true }
            }
        }
    }

    suspend fun hentBehandler(fnr: String): Behandler = runCatching {
        val url = "$baseUrl/api/v2/behandler"
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
        val url = "$baseUrl/api/v2/behandlerMedHprNummer"
        log.info { "Henter behandler data med url: $url" }
        val response = client.get(url) {
            headers["hprNummer"] = hprnr
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
    }.getOrElse { throw SyfohelsenettproxyClientException("Feil under henting av behandler data", it) }
}
