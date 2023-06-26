package no.nav.hjelpemidler.brille.hotsak

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.engineFactory
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.azureAD
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class HotsakClient(
    props: Configuration.HotsakApiProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.hotsak() },
) {
    private val baseUrl = props.baseUrl
    private val client = createHttpClient(engine) {
        expectSuccess = true
        azureAD(scope = props.scope) {
            cache(leeway = 10.seconds)
        }
    }

    suspend fun hentEksisterendeVedtaksDato(fnr: String, bestillingsdato: LocalDate): LocalDate? {
        try {
            val url = "$baseUrl/vilkarsvurdering/sjekk-vedtak"
            log.info { "Henter vedtaksdato data med url: $url" }
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(
                    SjekkVedtakDto(
                        fnr = fnr,
                        bestillingsdato = bestillingsdato,
                    )
                )
            }
            log.info { "Har fått response fra hm-saksbehandling med status: ${response.status}" }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val vedtaksdato = response.body<VedtakIKalenderåretDto>()
                    sikkerLog.info { "Fikk svar fra hm-saksbehandøing: $vedtaksdato" }
                    return vedtaksdato.vedtaksdato
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
        val baseUrlNoApp = baseUrl.removeSuffix("/").removeSuffix("/api")
        try {
            val url = "$baseUrlNoApp/deep-ping"
            log.info { "Kjører deep-ping mot hm-saksbehandling med url: $url" }
            val response = client.get(url) {
                expectSuccess = true // Vær eksplisitt i tilfelle noen endrer på den delte klienten.
            }
            log.info { "Har fått response fra hm-saksbehandling med status: ${response.status}" }
        } catch (clientReqException: ClientRequestException) {
            throw HotsakClientException("Feil under deep-ping mot hm-saksbehandling", clientReqException)
        } catch (e: Exception) {
            throw HotsakClientException("Ukjent feil under deep-ping mot hm-saksbehandling", e)
        }
    }

    data class SjekkVedtakDto(
        val fnr: String,
        val bestillingsdato: LocalDate
    )

    data class VedtakIKalenderåretDto(
        val vedtaksdato: LocalDate?
    )

}
