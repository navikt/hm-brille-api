package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

    enum class ResponseType(val type: String) {
        Person("Person"),
        Organization("Organization"),
    }

    data class ResponseObject(
        val partyUuid: String,
        val partyId: Long,
        val type: String,
        val unitType: String,
        val personId: String?,
        val organizationNumber: String?,
        val name: String,
        val isDeleted: Boolean,
        val onlyHierarchyElementWithNoAccess: Boolean,
        val authorizedResources: JsonNode,
        val authorizedRoles: List<String>,
        val subunits: List<ResponseObject>,
    )

    suspend fun test(fnr: String) {
        data class Reqeust(
            val type: String = "urn:altinn:person:identifier-no",
            val value: String,
        )

        log.info { "Requesting resources for $fnr" }
        val response = client.post("/accessmanagement/api/v1/resourceowner/authorizedparties?includeAltinn2=true") {
            setBody(Reqeust(value = fnr))
        }
        log.info { "Result: ${response.status}" }

        val result = response.body<List<ResponseObject>>()
        log.info { "Debug result: $result" }

        val orgs = result.filter {
            // Bare se på organisasjoner
            runCatching { ResponseType.valueOf(it.type) }.getOrNull() == ResponseType.Organization
        }.flatMap {
            // Pakk ut underenheter ala. gammel implementasjon
            listOf(
                *it.subunits.map { innerIt -> Pair(innerIt, it.organizationNumber) }.toTypedArray(),
                Pair(it.copy(subunits = emptyList()), null),
            )
        }
        log.info { "Organisasjoner: $orgs" }

        val avgivere = orgs.map {
            Avgiver(
                navn = it.first.name,
                orgnr = it.first.partyUuid,
                parentOrgnr = it.second,
            )
        }
        log.info { "Avgivere: $avgivere" }
    }
}
