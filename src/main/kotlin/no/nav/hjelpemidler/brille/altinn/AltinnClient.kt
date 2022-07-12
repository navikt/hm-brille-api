package no.nav.hjelpemidler.brille.altinn

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration
import no.nav.hjelpemidler.brille.jsonMapper

private val log = KotlinLogging.logger { }

const val ROLE_DEFINITION_ID_HOVEDADMINISTRATOR = "24869"

class AltinnClient(properties: Configuration.AltinnProperties) {
    private val client: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }
        defaultRequest {
            headers {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header("X-Consumer-ID", properties.proxyConsumerId)
                header("X-NAV-APIKEY", properties.apiGWKey)
                header("APIKEY", properties.apiKey)
            }
        }
    }
    private val baseUrl = properties.baseUrl

    suspend fun get(path: String, queryParameters: Parameters): JsonNode {
        val url = "$baseUrl/$path"
        log.info { "URL: $url, queryParameters: $queryParameters" }
        val response = client.get(url) {
            this.url.parameters.appendAll(queryParameters)
        }
        log.info { "Final URL: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body()
        }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return jsonMapper.nullNode()
    }

    suspend fun hentAvgivere(fnr: String): List<Avgiver> {
        val response =
            client.get("$baseUrl/reportees?ForceEIAuthentication&subject=$fnr&\$filter=Type+ne+'Person'+and+Status+eq+'Active'")
        if (response.status == HttpStatusCode.OK) {
            return response.body() ?: emptyList()
        }
        log.warn { "Fant ikke avgivere, status: ${response.status}" }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return emptyList() // todo -> feilhåndtering
    }

    suspend fun erHovedadministratorFor(fnr: String, orgnr: String): Boolean {
        val response =
            client.get("$baseUrl/authorization/roles?ForceEIAuthentication&subject=$fnr&reportee=$orgnr&language=1044&\$filter=RoleDefinitionId+eq+$ROLE_DEFINITION_ID_HOVEDADMINISTRATOR")
        if (response.status == HttpStatusCode.OK) {
            return response.body<List<JsonNode>?>()?.isNotEmpty() ?: false
        }
        log.warn { "Fant ikke hovedadministrator, status: ${response.status}" }
        kotlin.runCatching {
            val body = response.body<String>()
            log.warn { body }
        }
        return false // todo -> feilhåndtering
    }
}

data class Avgiver(
    @JsonProperty("Name")
    val navn: String,
    @JsonProperty("OrganizationNumber")
    val orgnr: String,
    @JsonProperty("ParentOrganizationNumber")
    val parentOrgnr: String?,
)
