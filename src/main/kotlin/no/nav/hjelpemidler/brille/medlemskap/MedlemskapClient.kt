package no.nav.hjelpemidler.brille.medlemskap

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.StatusCodeException
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient
import no.nav.hjelpemidler.http.openid.TokenSetProvider
import no.nav.hjelpemidler.http.openid.openID
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class MedlemskapClient(
    tokenSetProvider: TokenSetProvider,
    engine: HttpClientEngine = CIO.create(),
) {
    private val baseUrl = Configuration.MEDLEMSKAP_API_URL
    private val client = createHttpClient(engine) {
        expectSuccess = false
        openID(tokenSetProvider)
        install(HttpTimeout)
    }

    suspend fun slåOppMedlemskapBarn(
        fnr: String,
        bestillingsDato: LocalDate,
        correlationId: String,
    ): JsonNode {
        val response = client.post(baseUrl) {
            timeout {
                requestTimeoutMillis = 10_000
            }
            correlationId(correlationId)
            contentType(Json)
            setBody(
                Request(
                    fnr = fnr,
                    bestillingsdato = bestillingsDato,
                ),
            )
        }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        } else {
            val message = runCatching { response.body<String>() }.getOrElse {
                log.warn(it) { "Klarte ikke å hente response body som string" }
                ""
            }
            log.error { "${response.request.method.value} ${response.request.url} ga status: ${response.status}" }
            throw StatusCodeException(
                HttpStatusCode.InternalServerError,
                "Kall til medlemskap-barn ga status ${response.status}: $message",
            )
        }
    }
}

private data class Request(
    val fnr: String,
    val bestillingsdato: LocalDate?,
)
