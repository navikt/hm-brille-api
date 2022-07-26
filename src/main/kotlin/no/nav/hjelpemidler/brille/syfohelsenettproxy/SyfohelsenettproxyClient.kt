package no.nav.hjelpemidler.brille.syfohelsenettproxy

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.azuread.OpenIDClient
import no.nav.hjelpemidler.brille.azuread.azureAd
import no.nav.hjelpemidler.brille.engineFactory

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class SyfohelsenettproxyClient(
    private val baseUrl: String,
    private val scope: String,
    private val azureAdClient: OpenIDClient,
    engine: HttpClientEngine = engineFactory { StubEngine.syfohelsenettproxy() },
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
            azureAd(azureAdClient, scope)
        }
    }

    suspend fun hentBehandler(fnr: String): Behandler? {
        try {
            val url = "$baseUrl/api/v2/behandler"
            log.info { "Henter behandler data med url: $url" }
            val response = client.get(url) {
                headers["behandlerFnr"] = fnr
            }
            log.info { "Har fått response fra HPR med status: ${response.status}" }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val behandler = response.body<Behandler>()
                    sikkerLog.info { "Fikk svar fra HPR: $behandler" }
                    return behandler
                }
            }
            throw SyfohelsenettproxyClientException("Uventet svar fra tjeneste: ${response.status}", null)
        } catch (clientReqException: ClientRequestException) {
            if (clientReqException.message.contains("Fant ikke behandler")) {
                return null
            } else {
                throw SyfohelsenettproxyClientException("Feil under henting av behandler data", clientReqException)
            }
        } catch (e: Exception) {
            throw SyfohelsenettproxyClientException("Ukjent feil under henting av behandler data", e)
        }
    }

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
