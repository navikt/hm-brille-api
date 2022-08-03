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
const val ROLE_DEFINITION_CODE_HOVEDADMINISTRATOR = "HADM"

private val log = KotlinLogging.logger { }
private val sikkerLog = KotlinLogging.logger("tjenestekall")

class AltinnClient(props: Configuration.AltinnProperties) {
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
                header("X-Consumer-ID", props.proxyConsumerId)
                header("X-NAV-APIKEY", props.apiGWKey)
                header("APIKEY", props.apiKey)
            }
        }
    }
    private val baseUrl = props.baseUrl

    suspend fun hentAvgivere(fnr: String): List<Avgiver> {
        val response = client.get("$baseUrl/reportees") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("\$filter", "Type ne 'Person' and Status eq 'Active'")
                parameters.append("\$top", "200")
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
                parameters.append("\$filter", "RoleDefinitionCode eq '$ROLE_DEFINITION_CODE_HOVEDADMINISTRATOR'")
            }
        }
        sikkerLog.info { "Hentet roller med url: ${response.request.url}" }
        if (response.status == HttpStatusCode.OK) {
            return response.body<List<JsonNode>?>()?.isNotEmpty() ?: false
        }
        log.warn { "Kunne ikke hente roller, status: ${response.status}" }
        return false
    }

    suspend fun harRolleFor(fnr: String, orgnr: String, roller: AltinnRoller): Boolean {
        val response = client.get("$baseUrl/authorization/roles") {
            url {
                parameters.append("ForceEIAuthentication", "true")
                parameters.append("subject", fnr)
                parameters.append("reportee", orgnr)
                parameters.append("language", LANGUAGE_NORSK_BOKMÅL)
                parameters.append(
                    "\$filter",
                    "RoleDefinitionCode ${buildRolleQuery(roller)}"
                )
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
