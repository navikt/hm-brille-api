package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StatusCodeException
import no.nav.hjelpemidler.brille.azuread.azureAd
import java.time.LocalDate
import java.util.UUID

private val log = KotlinLogging.logger {}

class MedlemskapClient(
    props: Configuration.MedlemskapOppslagProperties,
    engine: HttpClientEngine = CIO.create(),
) {
    private val baseUrl = props.baseUrl
    private val scope = props.scope
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }
        install(Auth) {
            azureAd(scope)
        }
    }

    fun slåOppMedlemskap(fnr: String, bestillingsDato: LocalDate, correlationId: String = UUID.randomUUID().toString()): JsonNode = runBlocking {
        val response = client.post(baseUrl) {
            header("Nav-Call-Id", correlationId)
            header("X-Correlation-Id", correlationId)
            contentType(Json)
            setBody(
                Request(
                    fnr = fnr,
                    førsteDagForYtelse = bestillingsDato,
                    periode = RequestPeriode(bestillingsDato, bestillingsDato),
                    brukerinput = RequestBrukerinfo(false),
                )
            )
        }
        if (response.status == HttpStatusCode.OK) {
            response.body()
        } else {
            val message = runCatching { response.body<String>() }.getOrElse {
                log.warn(it) { "Klarte ikke å hente response body som string" }
                "${response.request.method.value} ${response.request.url} ga status: ${response.status}"
            }
            throw StatusCodeException(HttpStatusCode.InternalServerError, "Feil i kall til medlemskap: $message")
        }
    }
}

private data class Request(
    val fnr: String,
    val førsteDagForYtelse: LocalDate,
    val periode: RequestPeriode,
    val brukerinput: RequestBrukerinfo,
)

private data class RequestPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

private data class RequestBrukerinfo(
    val arbeidUtenforNorge: Boolean,
)
