package no.nav.hjelpemidler.brille.hotsak

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.MDC_CORRELATION_ID
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import no.nav.hjelpemidler.logging.teamInfo
import org.slf4j.MDC
import java.time.Instant
import java.time.LocalDate

private val log = KotlinLogging.logger { }

class HotsakClient(
    tokenSetProvider: TokenSetProvider,
    engine: HttpClientEngine = engineFactory(StubEngine::hotsak),
) {
    private val baseUrl = Configuration.HOTSAK_API_URL
    private val client = createHttpClient(engine) {
        expectSuccess = true
        openID(tokenSetProvider)
    }

    suspend fun hentEksisterendeVedtak(fnr: String, bestillingsdato: LocalDate): List<HotsakVedtak> {
        try {
            val url = "$baseUrl/vilkarsvurdering/sjekk-vedtak"
            log.info { "Henter vedtaksdato data med url: $url" }
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    HentEksisterendeVedtakRequest(
                        fnr = fnr,
                        bestillingsdato = bestillingsdato,
                    ),
                )
            }
            log.info { "Har fått response fra hm-saksbehandling med status: ${response.status}" }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val vedtak = response.body<HentEksisterendeVedtakResponse>()
                    log.teamInfo { "Fikk svar fra hm-saksbehandling: $vedtak" }
                    return vedtak.vedtak ?: listOfNotNull(vedtak.vedtaksdato).map {
                        HotsakVedtak(
                            sakId = "",
                            vedtakId = "",
                            vedtaksdato = Instant.now(), // vedtaksdato i response er egentlig bestillingsdato
                            vedtaksstatus = "",
                            bestillingsdato = it,
                        )
                    }
                }
            }
            throw HotsakClientException("Uventet svar fra tjeneste: ${response.status}", null)
        } catch (clientReqException: ClientRequestException) {
            throw HotsakClientException("Feil under henting av vedtaksdato data", clientReqException)
        } catch (e: Exception) {
            throw HotsakClientException("Ukjent feil under henting av vedtaksdato data", e)
        }
    }

    suspend fun deepPing() {
        val baseUrl = baseUrl.removeSuffix("/").removeSuffix("/api")
        try {
            val url = "$baseUrl/deep-ping"
            val uid = MDC.get(MDC_CORRELATION_ID)
            log.info { "Kjører deep-ping mot hm-saksbehandling med url: $url" }
            val response = client.get(url) {
                expectSuccess = true // Vær eksplisitt i tilfelle noen endrer på den delte klienten.
                header(HttpHeaders.XCorrelationId, uid)
            }
            log.info { "Har fått response fra hm-saksbehandling med status: ${response.status}" }
        } catch (e: ClientRequestException) {
            throw HotsakClientException("Feil under deep-ping mot hm-saksbehandling", e)
        } catch (e: Exception) {
            throw HotsakClientException("Ukjent feil under deep-ping mot hm-saksbehandling", e)
        }
    }

    data class HentEksisterendeVedtakRequest(
        val fnr: String,
        val bestillingsdato: LocalDate,
    )

    data class HentEksisterendeVedtakResponse(
        val vedtaksdato: LocalDate?,
        val vedtak: List<HotsakVedtak>? = null,
    )
}
