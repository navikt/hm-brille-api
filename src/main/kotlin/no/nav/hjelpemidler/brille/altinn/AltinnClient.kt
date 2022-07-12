package no.nav.hjelpemidler.brille.altinn

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
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import mu.KotlinLogging
import no.nav.hjelpemidler.brille.Configuration

const val LANGUAGE_NORSK_BOKMÅL = "1044"
const val ROLE_DEFINITION_ID_HOVEDADMINISTRATOR = "24869"

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

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

    suspend fun hentAvgivere(fnr: String): List<Avgiver> {
        val response = client.get("$baseUrl/reportees") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("\$filter", "Type ne 'Person' and Status eq 'Active'")
            }
        }
        sikkerLog.info { "Hentet avgivere med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body() ?: emptyList()
        }
        log.warn { "Kunne ikke hente avgivere, status: ${response.status}" }
        return emptyList()
    }

    suspend fun erHovedadministratorFor(fnr: String, orgnr: String): Boolean {
        val response = client.get("$baseUrl/authorization/roles") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("reportee", orgnr)
                parameters.append("language", LANGUAGE_NORSK_BOKMÅL)
                parameters.append("\$filter", "RoleDefinitionId eq $ROLE_DEFINITION_ID_HOVEDADMINISTRATOR")
            }
        }
        sikkerLog.info { "Hentet roller med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body<List<JsonNode>?>()?.isNotEmpty() ?: false
        }
        log.warn { "Kunne ikke hente roller, status: ${response.status}" }
        return false
    }
}
