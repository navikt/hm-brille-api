package no.nav.hjelpemidler.brille.altinn

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.configuration.MaskinportenEnvironmentVariable
import no.nav.hjelpemidler.http.correlationId
import no.nav.hjelpemidler.http.createHttpClient

private val log = KotlinLogging.logger {}

class Altinn3Client {
    private val client: io.ktor.client.HttpClient = createHttpClient {
        maskinporten(MaskinportenEnvironmentVariable.MASKINPORTEN_SCOPES)
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                correlationId()
            }
            url(Configuration.ALTINN_URL)
        }
    }

    suspend fun test(fnr: String) {
        data class Reqeust(
            val type: String = "urn:altinn:person:identifier-no",
            val value: String,
        )

        log.info { "Requesting resources for $fnr" }
        val result = client.post("/accessmanagement/api/v1/resourceowner/authorizedparties?includeAltinn2=true") {
            setBody(Reqeust(value = fnr))
        }
        log.info { "Result: ${result.status}" }

        val body = result.bodyAsText()
        log.info { "Body: $body" }
    }
}
