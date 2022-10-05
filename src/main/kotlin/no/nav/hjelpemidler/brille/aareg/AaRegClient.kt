package no.nav.hjelpemidler.brille.aareg

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
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StubEngine
import no.nav.hjelpemidler.brille.aareg.model.ArbeidsforholdDto
import no.nav.hjelpemidler.brille.azuread.azureAd
import no.nav.hjelpemidler.brille.engineFactory

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AaRegClient(
    props: Configuration.AaRegProperties,
    engine: HttpClientEngine = engineFactory { StubEngine.aareg() },
) {
    private val baseUrl = props.baseUrl
    private val scope = props.scope
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
            azureAd(scope)
        }
    }

    suspend fun hentArbeidsforhold(fnr: String): List<ArbeidsforholdDto> {
        try {
            val url = "$baseUrl/api/v2/arbeidstaker"
            log.info { "Henter arbeidsforhold med url: $url" }
            val response = client.get(url) {
                headers["Nav-Personident"] = fnr
            }
            log.info { "Har fått response fra arbeidsforhold med status: ${response.status}" }
            when (response.status) {
                HttpStatusCode.OK -> {
                    val arbeidsforhold = response.body<List<ArbeidsforholdDto>>()
                    sikkerLog.info { "Fikk svar fra arbeidsforhold: $arbeidsforhold" }
                    return arbeidsforhold
                }
            }
            throw AargClientException("Uventet svar fra tjeneste: ${response.status}", null)
        } catch (clientReqException: ClientRequestException) {
            throw AargClientException("Feil under henting av arbeidsforhold", clientReqException)
        } catch (e: Exception) {
            throw AargClientException("Ukjent feil under henting av arbeidsforhold", e)
        }
    }
}

fun Route.hentArbeidsforhold(aaRegClient: AaRegClient) {
    post("/admin/hentArbeidsforhold") {
        kotlin.runCatching {
            val fnrInnloggetBruker = "15084300133"

            val arbeidsforhold: List<ArbeidsforholdDto> =
                runCatching { runBlocking { aaRegClient.hentArbeidsforhold(fnrInnloggetBruker) } }.getOrElse {
                    log.error(it) { "Feil oppstod ved kall mot aareg" }
                    throw it
                }

            data class Response(
                val arbeidsforhold: List<ArbeidsforholdDto>,
            )

            call.respond(
                Response(
                    arbeidsforhold = arbeidsforhold
                )
            )
        }.getOrElse {
            log.error(it) { "henting av arbeidsforhold feilet!" }
            call.respond(HttpStatusCode.InternalServerError, it.stackTraceToString())
        }
    }
}
