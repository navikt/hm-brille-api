package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StatusCodeException
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.azureAD
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

class MedlemskapClient(
    props: Configuration.MedlemskapOppslagProperties,
    engine: HttpClientEngine = CIO.create(),
) {
    private val baseUrl = props.baseUrl
    private val baseUrl2 = props.baseUrl2
    private val client = createHttpClient(engine) {
        expectSuccess = false
        azureAD(scope = props.scope) {
            cache(leeway = 10.seconds)
        }
    }
    private val client2 = createHttpClient(engine) {
        expectSuccess = false
        azureAD(scope = props.scope2) {
            cache(leeway = 10.seconds)
        }
    }

    suspend fun slåOppMedlemskapBarn(
        fnr: String,
        bestillingsDato: LocalDate,
        correlationId: String = UUID.randomUUID().toString(),
    ): JsonNode {
        val response = client2.post(baseUrl2) {
            header("Nav-Call-Id", correlationId)
            header("X-Correlation-Id", correlationId)
            contentType(Json)
            setBody(
                Request2(
                    fnr = fnr,
                    bestillingsDato = bestillingsDato,
                )
            )
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            val message = runCatching { response.body<String>() }.getOrElse {
                log.warn(it) { "Klarte ikke å hente response body som string" }
                "${response.request.method.value} ${response.request.url} ga status: ${response.status}"
            }
            throw StatusCodeException(HttpStatusCode.InternalServerError, "Feil i kall til medlemskap-barn: $message")
        }
    }

    suspend fun slåOppMedlemskap(
        fnr: String,
        bestillingsDato: LocalDate,
        correlationId: String = UUID.randomUUID().toString(),
    ): JsonNode {
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
            return response.body()
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

private data class Request2(
    val fnr: String,
    val bestillingsDato: LocalDate?,
)
